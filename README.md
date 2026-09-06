## 1. 接口约定

### 1.1 基础信息

- 默认服务地址：`http://<host>:8080`
- 接口统一前缀：`/api`
- 请求方式：HTTP
- 字符编码：UTF-8
- JSON 请求头：`Content-Type: application/json`
- 文件上传请求头：`Content-Type: multipart/form-data`

生产环境的端口和敏感配置以部署环境变量为准。

### 1.2 登录鉴权

已实现的鉴权方式为双 Token：

| Token          | 用途               | 默认有效期 | 请求携带方式         |
|----------------|--------------------|------------|----------------------|
| `accessToken`  | 访问需要登录的接口 | 2 小时     | 请求头 `token`       |
| `refreshToken` | 刷新登录状态       | 7 天       | 放在刷新接口请求体中 |

注意：当前后端读取的请求头名称是 `token`，不是 `Authorization: Bearer <token>`。

登录和刷新 Token 接口不需要携带 `accessToken`；完善资料、头像上传、退出登录以及文件上传接口必须携带有效的 `accessToken`。

刷新成功后，服务端会重新签发一对 Token，并覆盖 Redis 中该用户原有的 Token。客户端必须同时保存并替换新的 `accessToken` 和
`refreshToken`。

### 1.3 统一响应结构

成功响应：

```json
{
  "code": 1,
  "msg": "操作成功",
  "data": {}
}
```

无业务数据时，`data` 为 `null`：

```json
{
  "code": 1,
  "msg": "操作成功",
  "data": null
}
```

当前异常处理器的实际行为如下：

| 场景                   | HTTP 状态 |            `code` | `msg`                  | `data`            |
|------------------------|----------:|------------------:|------------------------|-------------------|
| 成功                   |       200 |               `1` | 操作成功               | 业务数据或 `null` |
| 业务异常               |       200 | 对应 `ResultCode` | 业务异常信息           | `null`            |
| 参数校验失败           |       200 |           `10003` | 参数校验失败           | 参数错误数组      |
| Token 无效、过期或缺失 |       401 |           `10005` | 登录已过期，请重新登录 | `null`            |
| 未处理系统异常         |       200 |           `10004` | 系统错误，请稍后重试   | `null`            |

业务错误码：

|  `code` | 常量                 | 含义                   |
|--------:|----------------------|------------------------|
| `10001` | `USER_NOT_EXIST`     | 用户不存在             |
| `10002` | `WECHAT_AUTH_FAILED` | 微信登录失败           |
| `10003` | `PARAM_ERROR`        | 参数校验失败           |
| `10004` | `SYSTEM_ERROR`       | 系统错误，请稍后重试   |
| `10005` | `LOGIN_EXPIRED`      | 登录已过期，请重新登录 |
| `10006` | `FILE_TYPE_NOT_SUPPORTED` | 不支持的文件类型 |
| `10007` | `FILE_SIZE_EXCEEDED`  | 文件大小超出限制 |
| `10008` | `CONTENT_VIOLATION`   | 内容违规，请勿上传 |
| `10009` | `WECHAT_SECURITY_ERROR` | 内容安全校验失败，请稍后重试 |
| `10010` | `FILE_SAVE_FAILED`    | 文件保存失败 |

客户端应同时依据 HTTP 状态和响应中的 `code`、`msg` 处理异常。

Token 无效时的响应示例：

```http
HTTP/1.1 401 Unauthorized
Content-Type: application/json;charset=UTF-8

{
  "code": 10005,
  "msg": "登录已过期，请重新登录",
  "data": null
}
```

参数错误数组格式：

```json
{
  "code": 10003,
  "msg": "参数校验失败",
  "data": [
    {
      "argumentName": "code",
      "message": "code 不能为空"
    }
  ]
}
```

## 2. 微信小程序登录流程

```text
小程序 wx.login()
      ↓ 获取临时 code
POST /api/user/login
      ↓
后端调用微信 jscode2session
      ↓ 获取 openid
查询或创建用户
      ↓
签发 accessToken + refreshToken
      ↓
小程序保存 Token
```

临时 `code` 只能使用一次，必须由小程序在登录时实时获取，不能缓存后重复提交。

微信 `jscode2session` 是服务端内部调用，不是提供给小程序直接调用的业务接口。`appid`、`secret` 和接口地址由服务端配置项
`wechat.*` 管理，不能下发到小程序端。

## 3. 已实现接口

| 接口                | 方法   | 是否鉴权 | 说明                                 |
|---------------------|--------|----------|--------------------------------------|
| `/api/user/login`   | `POST` | 否       | 微信小程序登录，首次登录自动创建用户 |
| `/api/user/refresh` | `POST` | 否       | 使用刷新 Token 获取新 Token 对       |
| `/api/user/profile` | `POST` | 是       | 更新并返回当前用户资料               |
| `/api/user/avatar`  | `POST` | 是       | 上传头像：校验后存 OSS，返回永久 URL（不落库） |
| `/api/user/logout`  | `POST` | 是       | 删除当前用户在 Redis 中的登录 Token  |
| `/api/document/upload` | `POST` | 是    | 上传 PDF/Word/图片并创建分析任务     |

### 3.1 微信小程序登录

#### 基本信息

- 接口：`POST /api/user/login`
- 鉴权：不需要
- 请求头：`Content-Type: application/json`
- 业务说明：服务端通过微信临时 `code` 换取 `openid`；如果用户不存在则自动创建用户。

#### 请求参数

| 参数名 | 类型     | 必填 | 说明                                       |
|--------|----------|------|--------------------------------------------|
| `code` | `String` | 是   | 小程序调用 `wx.login()` 获取的临时登录凭证 |

请求示例：

```http
POST /api/user/login HTTP/1.1
Host: localhost:8080
Content-Type: application/json

{
  "code": "微信 wx.login() 返回的临时 code"
}
```

#### 成功响应

```json
{
  "code": 1,
  "msg": "操作成功",
  "data": {
    "id": "1",
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
  }
}
```

| `data` 字段    | 类型     | 说明                                                                      |
|----------------|----------|---------------------------------------------------------------------------|
| `id`           | `String` | 用户 ID。后端将 `Long` 序列化为字符串，避免小程序 JavaScript 数字精度丢失 |
| `accessToken`  | `String` | 访问 Token                                                                |
| `refreshToken` | `String` | 刷新 Token                                                                |

当前登录接口只返回用户 ID 和 Token，不返回昵称、头像等用户基本信息。若前端需要展示用户资料，应在登录后调用“更新用户资料”接口，或后续增加独立的用户信息查询接口。

#### 失败场景

| 场景                   | 响应消息                                      |
|------------------------|-----------------------------------------------|
| `code` 为空            | `参数校验失败`，`data` 中包含 `code 不能为空` |
| 微信换取 `openid` 失败 | `微信登录失败: <微信错误信息>`                |
| 服务端异常             | `系统错误，请稍后重试`                        |

### 3.2 刷新 Token

#### 基本信息

- 接口：`POST /api/user/refresh`
- 鉴权：不需要 `accessToken`
- 请求头：`Content-Type: application/json`

#### 请求参数

| 参数名         | 类型     | 必填 | 说明                                 |
|----------------|----------|------|--------------------------------------|
| `refreshToken` | `String` | 是   | 上一次登录或刷新成功返回的刷新 Token |

请求示例：

```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
}
```

#### 成功响应

响应结构与登录接口相同：

```json
{
  "code": 1,
  "msg": "操作成功",
  "data": {
    "id": "1",
    "accessToken": "新的访问 Token",
    "refreshToken": "新的刷新 Token"
  }
}
```

#### 失败场景

| 场景                              | 响应消息                                              |
|-----------------------------------|-------------------------------------------------------|
| `refreshToken` 为空               | `参数校验失败`，`data` 中包含 `refreshToken 不能为空` |
| Token 签名、类型或 Redis 校验失败 | `登录已过期，请重新登录`                              |
| 用户不存在                        | `用户不存在`                                          |

### 3.3 更新用户资料

#### 基本信息

- 接口：`POST /api/user/profile`
- 鉴权：需要
- 请求头：`token: <accessToken>`
- 业务说明：更新当前登录用户的昵称和头像，并返回更新后的用户信息。

#### 请求参数

| 参数名     | 类型     | 必填 | 说明                                   |
|------------|----------|------|----------------------------------------|
| `nickname` | `String` | 是   | 用户昵称；当前实现为必填非空（`@NotBlank`） |
| `avatar`   | `String` | 否   | 用户头像 URL；不传或为 `null` 时不更新（建议先通过 §3.5 头像上传接口获取） |

请求示例：

```http
POST /api/user/profile HTTP/1.1
Host: localhost:8080
Content-Type: application/json
token: eyJhbGciOiJIUzI1NiJ9...

{
  "nickname": "安心用户",
  "avatar": "https://example.com/avatar.png"
}
```

也可以只更新其中一个字段：

```json
{
  "nickname": "新昵称"
}
```

#### 成功响应

```json
{
  "code": 1,
  "msg": "操作成功",
  "data": {
    "id": "1",
    "nickname": "安心用户",
    "avatar": "https://example.com/avatar.png"
  }
}
```

| `data` 字段 | 类型               | 说明         |
|-------------|--------------------|--------------|
| `id`        | `String`           | 用户 ID      |
| `nickname`  | `String` 或 `null` | 用户昵称     |
| `avatar`    | `String` 或 `null` | 用户头像 URL |

### 3.4 退出登录

#### 基本信息

- 接口：`POST /api/user/logout`
- 鉴权：需要
- 请求头：`token: <accessToken>`
- 请求体：无

请求示例：

```http
POST /api/user/logout HTTP/1.1
Host: localhost:8080
token: eyJhbGciOiJIUzI1NiJ9...
```

成功响应：

```json
{
  "code": 1,
  "msg": "操作成功",
  "data": null
}
```

退出登录会删除 Redis 中当前用户的访问 Token 和刷新 Token。客户端收到成功响应后应清理本地保存的 Token。

### 3.5 头像上传

#### 基本信息

- 接口：`POST /api/user/avatar`
- 鉴权：需要
- 请求头：`Content-Type: multipart/form-data`，字段名 `file`
- 业务说明：校验通过后存储到阿里云 OSS 并返回永久 URL。**本接口不落库**，前端需将返回的 URL 作为 `avatar` 随昵称一起提交 `POST /api/user/profile` 完成保存。

#### 限制（符合微信小程序头像规范）

| 项目       | 限制                                                                 |
|------------|----------------------------------------------------------------------|
| 大小       | 不可超过 2MB（超过返回 `10007`）                                     |
| 格式       | BMP、JPEG、JPG、GIF、PNG（按文件真实内容校验，伪装后缀会被拒绝）    |
| 内容安全   | 通过微信 imgSecCheck 同步审核，违规（87014）返回 `10008`，文件不入库 |
| 形状尺寸   | 头像始终以正方形（1:1）展示，建议上传正方形图片；前端可使用 `wx.chooseAvatar` 获得已裁剪的头像 |

#### 请求示例

```http
POST /api/user/avatar HTTP/1.1
Host: localhost:8080
Content-Type: multipart/form-data
token: eyJhbGciOiJIUzI1NiJ9...

file=<二进制图片>
```

#### 成功响应

```json
{
  "code": 1,
  "msg": "头像上传成功",
  "data": {
    "avatar": "https://anxin-demo.oss-cn-hangzhou.aliyuncs.com/avatars/20260906/{uuid}.jpg"
  }
}
```

#### 失败场景

| 场景                         | 响应消息                                    |
|------------------------------|---------------------------------------------|
| 未选择文件                   | `请选择头像文件`（`code=10003`）            |
| 大小超过 2MB                 | `头像大小不能超过2MB`（`code=10007`）       |
| 真实类型不在白名单          | `头像仅支持 BMP/JPEG/JPG/GIF/PNG 格式图片`（`code=10006`） |
| 微信判定内容违规            | `内容违规，请勿上传`（`code=10008`）        |
| 微信内容安全服务不可用      | `内容安全校验失败，请稍后重试`（`code=10009`，需配置真实 appid/secret） |
| OSS 存储失败                | `文件保存失败`（`code=10010`）              |

小程序端配合示例（拿到 URL 后再提交资料）：

```javascript
wx.uploadFile({
    url: 'http://localhost:8080/api/user/avatar',
    filePath: tempFilePath,
    name: 'file',
    header: {
        token: wx.getStorageSync('accessToken')
    },
    success(response) {
        const result = JSON.parse(response.data)
        if (result.code === 1) {
            // 将 result.data.avatar 与昵称一起提交 POST /api/user/profile
        }
    }
})
```

### 3.6 文件上传（PDF / Word / 图片，创建分析任务）

#### 基本信息

- 接口：`POST /api/document/upload`
- 鉴权：需要
- 请求头：`Content-Type: multipart/form-data`，字段名 `file`
- 业务说明：校验通过后存 OSS，落库 `document` 与 `analysis_task`（PENDING）并投递异步任务，立即返回任务 ID；不等待解析完成。

#### 支持的类型与大小

| 类型              | 扩展名             | 允许的真实 MIME（Tika 检测，仅改后缀无法绕过） | 大小上限 |
|-------------------|--------------------|------------------------------------------------|----------|
| PDF               | `.pdf`             | `application/pdf`                              | 10MB     |
| Word 2003         | `.doc`             | `application/msword`                           | 10MB     |
| Word 2007+        | `.docx`            | `application/vnd.openxmlformats-officedocument.wordprocessingml.document` | 10MB |
| 图片              | `.jpg` `.jpeg` `.png` | `image/jpeg`、`image/png`                   | 5MB      |

- 全局 multipart 上限 12MB；超过上限返回 `10007`，服务端不会读取文件内容。
- 文件以「UUID + Tika 真实后缀」命名存储于 OSS，不拼接用户原始文件名（防路径穿越）。

#### 内容安全

- 图片 ≤4MB：imgSecCheck **同步**审核，违规（87014）返回 `10008`，文件不会进入 OSS/数据库；
- 图片 >4MB 与 PDF/Word：走微信异步审核（mediaCheckAsync），当前为骨架（mock 放行），待接入回调。

#### 成功响应（已实现）

```http
POST /api/document/upload HTTP/1.1
Host: localhost:8080
Content-Type: multipart/form-data
token: eyJhbGciOiJIUzI1NiJ9...

file=<二进制文件>
```

```json
{
  "code": 1,
  "msg": "文件上传成功，分析任务已创建",
  "data": {
    "documentId": "10001",
    "taskId": "20001",
    "status": "PENDING"
  }
}
```

#### 失败场景

| 场景                      | 响应消息                                          |
|---------------------------|---------------------------------------------------|
| 未选择文件                | `请选择要上传的文件`（`code=10003`）              |
| 真实类型不在白名单        | `不支持的文件类型，仅支持 PDF/Word 与 jpg/png 图片`（`code=10006`） |
| 大小超限                  | `图片大小不能超过5MB` / `文档大小不能超过10MB`（`code=10007`） |
| 微信判定内容违规          | `内容违规，请勿上传`（`code=10008`）              |
| 微信内容安全服务不可用    | `内容安全校验失败，请稍后重试`（`code=10009`）    |
| OSS 存储失败              | `文件保存失败`（`code=10010`）                    |

#### 任务说明

上传成功后 `analysis_task` 状态按 `PENDING → PROCESSING → SUCCESS/FAILED` 异步流转（当前处理器为骨架：创建后直接置 `SUCCESS`，文档解析与 LLM 分析待接入）；任务状态查询接口见 §5.2。

## 4. 小程序调用示例

以下示例展示首次登录和保存 Token 的基本流程：

```javascript
wx.login({
    success(loginResult) {
        wx.request({
            url: 'http://localhost:8080/api/user/login',
            method: 'POST',
            header: {
                'content-type': 'application/json'
            },
            data: {
                code: loginResult.code
            },
            success(response) {
                const result = response.data
                if (result.code === 1) {
                    wx.setStorageSync('accessToken', result.data.accessToken)
                    wx.setStorageSync('refreshToken', result.data.refreshToken)
                    wx.setStorageSync('userId', result.data.id)
                }
            }
        })
    }
})
```

调用需要登录的接口时：

```javascript
wx.request({
    url: 'http://localhost:8080/api/user/profile',
    method: 'POST',
    header: {
        'content-type': 'application/json',
        token: wx.getStorageSync('accessToken')
    },
    data: {
        nickname: '安心用户'
    }
})
```

## 5. 规划中接口

以下接口按照需求文档中的“文件管理、分析任务、风险报告、RAG 问答和历史记录”整理。文件上传与分析任务创建已实现（见
§3.6），其余接口尚未实现对应 Controller。路径和字段可在开发时继续确认，但建议沿用本文档的统一响应结构和 Token 鉴权方式。

### 5.1 文件管理

| 接口                                   | 方法     | 鉴权 | 说明                                | 状态   |
|----------------------------------------|----------|------|-------------------------------------|--------|
| `/api/document/list`                   | `GET`    | 是   | 查询当前用户的文件和历史分析记录    | 规划中 |
| `/api/document/{documentId}`           | `GET`    | 是   | 查询文件详情                        | 规划中 |
| `/api/document/{documentId}`           | `DELETE` | 是   | 删除当前用户的文件                  | 规划中 |
| `/api/document/{documentId}/reanalyze` | `POST`   | 是   | 对已有文件重新发起分析              | 规划中 |

#### 文件列表

建议请求：

```http
GET /api/document/list?page=1&pageSize=10&status=ALL HTTP/1.1
Host: localhost:8080
token: eyJhbGciOiJIUzI1NiJ9...
```

建议返回字段：

| 字段          | 类型     | 说明                                 |
|---------------|----------|--------------------------------------|
| `id`          | `String` | 文件 ID                              |
| `fileName`    | `String` | 原始文件名                           |
| `fileType`    | `String` | `PDF`、`DOC`、`DOCX` 或 `IMAGE`      |
| `fileSize`    | `Long`   | 文件大小，单位 Byte                  |
| `status`      | `String` | 文件处理状态                         |
| `summary`     | `String` | 风险摘要                             |
| `createdTime` | `String` | 创建时间，格式 `yyyy-MM-dd HH:mm:ss` |
| `updatedTime` | `String` | 更新时间                             |

### 5.2 分析任务

| 接口                          | 方法  | 鉴权 | 说明                 | 状态   |
|-------------------------------|-------|------|----------------------|--------|
| `/api/analysis/task/{taskId}` | `GET` | 是   | 查询异步分析任务状态 | 规划中 |

建议响应：

```json
{
  "code": 1,
  "msg": "操作成功",
  "data": {
    "taskId": "20001",
    "documentId": "10001",
    "taskType": "RISK_ANALYSIS",
    "status": "PROCESSING",
    "retryCount": 0,
    "errorMessage": null,
    "startedTime": "2026-09-05 10:00:00",
    "finishedTime": null
  }
}
```

任务状态建议统一为：

| 状态         | 含义     |
|--------------|----------|
| `PENDING`    | 待处理   |
| `PROCESSING` | 处理中   |
| `SUCCESS`    | 分析成功 |
| `FAILED`     | 分析失败 |

分析采用异步处理：上传接口只负责完成文件校验、保存文件、创建任务和投递消息（已实现，任务状态机见 §6.2）。当前异步处理器为骨架，任务创建后直接置为
`SUCCESS`；待接入文档解析与 LLM 分析后，客户端可根据任务状态轮询查询接口。

### 5.3 风险报告

| 接口                                        | 方法  | 鉴权 | 说明             | 状态   |
|---------------------------------------------|-------|------|------------------|--------|
| `/api/document/{documentId}/report`         | `GET` | 是   | 查看文件风险报告 | 规划中 |
| `/api/document/{documentId}/risks/{riskId}` | `GET` | 是   | 查看单条风险详情 | 规划中 |

建议报告响应：

```json
{
  "code": 1,
  "msg": "操作成功",
  "data": {
    "documentId": "10001",
    "taskId": "20001",
    "riskScore": 78,
    "riskLevel": "HIGH",
    "riskSummary": "文件存在较高风险条款，建议重点核查违约责任和免责条款。",
    "riskStatistics": {
      "highCount": 3,
      "mediumCount": 5,
      "lowCount": 2
    },
    "risks": [
      {
        "id": "30001",
        "sectionId": "40001",
        "riskType": "违约责任",
        "riskLevel": "HIGH",
        "title": "提前解约责任过重",
        "originalText": "乙方提前解除合同，应支付剩余租期全部租金……",
        "reason": "该条款可能导致用户在提前解除合同时承担较高经济责任。",
        "impact": "可能增加提前解约成本。",
        "suggestion": "重点确认提前解除条件及违约责任。",
        "pageNo": 6,
        "startPosition": 120,
        "endPosition": 145
      }
    ],
    "overallSuggestion": "本报告仅作为 AI 辅助风险提示，不作为法律结论。"
  }
}
```

风险评分建议按需求文档执行：`0～39` 为低风险，`40～69` 为中风险，`70～100` 为高风险。风险报告必须保留原文、原因、影响、建议以及页码/位置，供前端进行原文溯源和定位。

### 5.4 RAG / Agent 智能问答

| 接口                                       | 方法     | 鉴权 | 说明                   | 状态   |
|--------------------------------------------|----------|------|------------------------|--------|
| `/api/document/{documentId}/chat/sessions` | `POST`   | 是   | 创建文件问答会话       | 规划中 |
| `/api/document/{documentId}/chat/sessions` | `GET`    | 是   | 查询文件的问答会话列表 | 规划中 |
| `/api/chat/sessions/{sessionId}/messages`  | `POST`   | 是   | 基于当前文件提问       | 规划中 |
| `/api/chat/sessions/{sessionId}/messages`  | `GET`    | 是   | 查询历史消息           | 规划中 |
| `/api/chat/sessions/{sessionId}`           | `DELETE` | 是   | 关闭问答会话           | 规划中 |

创建会话请求示例：

```json
{
  "title": "租赁合同风险咨询"
}
```

提问请求示例：

```json
{
  "content": "这个合同主要有哪些风险？"
}
```

建议回答结构：

```json
{
  "code": 1,
  "msg": "操作成功",
  "data": {
    "messageId": "50001",
    "role": "ASSISTANT",
    "content": "根据合同中的违约责任和自动续约条款，当前文件可能存在以下风险……",
    "references": [
      {
        "sectionId": "40001",
        "sectionNo": "第15条",
        "title": "违约责任",
        "content": "乙方提前解除合同……",
        "pageNo": 6
      }
    ],
    "createdTime": "2026-09-05 10:05:00"
  }
}
```

问答接口实现时应遵循以下约束：

1. 优先通过 RAG 检索当前文件原文，再交给模型生成回答。
2. 尽可能返回引用章节，支持前端“查看原文”。
3. 找不到文件依据时明确说明，不应编造条款内容。
4. 回答应使用“可能存在风险”等审慎表述，不输出确定性的法律结论。
5. 应校验会话、文件和用户的归属关系，禁止跨用户访问。

## 6. 数据与状态约定

### 6.1 ID 类型

数据库 ID 使用 `BIGINT`。对外 JSON 返回时建议统一序列化为字符串，避免小程序 JavaScript 数字精度丢失。当前登录、用户资料接口已经按此规则返回用户
ID。

### 6.2 文件状态

数据库 `document.status` 当前设计为：

| 数值 | 建议对外值   | 含义     |
|-----:|--------------|----------|
|  `0` | `PENDING`    | 待处理   |
|  `1` | `PROCESSING` | 处理中   |
|  `2` | `SUCCESS`    | 处理完成 |
|  `3` | `FAILED`     | 处理失败 |

> 注：自 0.1.3 起 `document.status` 与 `analysis_task.status` 均已按此约定落地（对应枚举 `TaskStatus`，上传创建任务时写入 `PENDING`）。

### 6.3 风险等级

风险详情中的 `riskLevel` 使用大写枚举值：`HIGH`、`MEDIUM`、`LOW`。

### 6.4 时间格式

服务端 Jackson 配置的时间格式为：`yyyy-MM-dd HH:mm:ss`，时区为 `Asia/Shanghai`。

## 7. 当前实现注意事项

1. `application.yml` 中微信 `appid` 和 `secret` 仍为空占位值。联调前必须在安全的环境配置中填写真实值，不要将 `secret`
   提交到前端或代码仓库。
2. 登录接口当前没有返回昵称和头像，与需求文档“用户基本信息”的输出要求存在差异。
3. 当前 `/api/user/profile` 是更新资料接口，不是只读的用户信息查询接口。
4. 当前鉴权请求头为 `token`；如果前端统一使用 `Authorization`，需要同步修改后端拦截器和本文档。
5. 上传限制与存储已确定：全局 multipart 上限 12MB；图片 ≤5MB、PDF/Word ≤10MB；文件存储于阿里云 OSS（公共读，
   配置见 `anxin.oss.*` 环境变量），对象名由服务端生成（UUID + Tika 真实后缀），不拼接用户原始文件名。
6. 微信内容安全依赖真实 `secret`：图片同步审核（imgSecCheck）未配置或失败时返回 `10009`；违规内容（87014）返回
   `10008`，且不会进入 OSS/数据库（先审核后入库）。
7. mediaCheckAsync 异步审核与文档解析/LLM 分析目前为骨架（mock 放行），异步任务创建后直接置 `SUCCESS`；
   分页参数上限与 AI 接口限流值待定。

## 8. 版本记录

| 版本  | 日期       | 说明                                                            |
|-------|------------|-----------------------------------------------------------------|
| 0.1.0 | 2026-09-05 | 初版：整理用户登录相关已实现接口，并补充需求对应的规划接口      |
| 0.1.1 | 2026-09-05 | 统一鉴权失败响应格式，并补充业务错误码返回说明                  |
| 0.1.2 | 2026-09-05 | 移除账号冻结机制：user 表删除 status 字段，错误码重排为连续编号 |
| 0.1.3 | 2026-09-06 | 新增头像上传（§3.5）与文件上传（§3.6）；OSS 存储与微信内容安全校验落地；分析任务异步链路与状态机落地；错误码补充 10006~10010 |
