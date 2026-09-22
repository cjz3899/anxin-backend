#!/usr/bin/env bash
# 生成云托管「服务设置 → 环境变量」要贴的内容，值从本地 application-dev.yml 里取，
# 免得手抄密钥抄错。输出两份：KEY=VALUE 文本 与 JSON 对象，控制台认哪种就用哪种。
#
# 故意不包含云托管里已经配好的 MYSQL_ADDRESS / MYSQL_USERNAME / MYSQL_PASSWORD，
# 那些是平台侧的既有值，脚本不去覆盖也不把它们的明文落到新文件里。
set -euo pipefail

cd "$(dirname "$0")/.."

DEV_YML="anxin-web/src/main/resources/application-dev.yml"
OUT_DIR="build/cloudrun"
if [[ ! -f "$DEV_YML" ]]; then
  echo "找不到 $DEV_YML（它被 .gitignore 排除，只存在本机上）" >&2
  exit 1
fi
mkdir -p "$OUT_DIR"

# 取 application-dev.yml 里某个 key 的值，去掉行尾注释
pick() {
  awk -v key="$1" '$0 ~ "^ *"key":" { sub(/#.*/, "", $0); sub(/^[^:]*:[ ]*/, ""); gsub(/^"|"$/, ""); print; exit }' "$DEV_YML"
}

OSS_ENDPOINT=$(pick endpoint)
OSS_AK=$(pick access-key-id)
OSS_SK=$(pick access-key-secret)
OSS_BUCKET=$(pick bucket)
WX_APPID=$(pick appid)
WX_SECRET=$(pick secret)
AI_KEY=$(pick api-key)
AI_BASE=$(pick base-url)
AI_MODEL=$(pick model)

for pair in OSS_ENDPOINT="$OSS_ENDPOINT" OSS_ACCESS_KEY_ID="$OSS_AK" OSS_BUCKET="$OSS_BUCKET" \
            WECHAT_APPID="$WX_APPID" AI_API_KEY="$AI_KEY" AI_BASE_URL="$AI_BASE" AI_MODEL="$AI_MODEL"; do
  if [[ -z "${pair#*=}" ]]; then
    echo "${pair%%=*} 取值为空，请检查 $DEV_YML 或手动补" >&2
  fi
done

# JWT 密钥必须新生成：仓库 dev 配置里那两串是明文示例，不能上生产。
# 但重复跑脚本不能每次都换密钥——控制台里已经贴过一次的话，换了就会出现
# "前端 token 有效但后端校验不过"这种极难定位的现象，所以已生成过就沿用
TEXT="$OUT_DIR/cloudrun-env.txt"
JSON="$OUT_DIR/cloudrun-env.json"

read_existing_secret() {
  [[ -f "$TEXT" ]] || return 0
  awk -v key="$1" -F= "\$1 == key { print \$2; exit }" "$TEXT"
}

JWT_ACCESS="$(read_existing_secret JWT_ACCESS_SECRET)"
JWT_REFRESH="$(read_existing_secret JWT_REFRESH_SECRET)"
if [[ -z "$JWT_ACCESS" || -z "$JWT_REFRESH" ]]; then
  JWT_ACCESS=$(openssl rand -hex 32)
  JWT_REFRESH=$(openssl rand -hex 32)
  echo "已生成新的 JWT 密钥对（若控制台已贴过旧值，请勿用本次结果覆盖）"
else
  echo "沿用已生成的 JWT 密钥，未重新生成"
fi

# 云托管 MySQL 的内网地址，以控制台「MySQL → 基础信息 → 网络信息」为准。
# 环境里原有的 MYSQL_ADDRESS 可能是早期值，别拿它当现状；地址变了这样重跑即可：
#   MYSQL_HOST=10.x.x.x ./scripts/gen-cloudrun-env.sh
MYSQL_HOST="${MYSQL_HOST:-10.12.107.186}"

cat > "$TEXT" <<EOF
MYSQL_HOST=$MYSQL_HOST
MYSQL_DB=anxin_db
REDIS_HOST=TODO-云托管环境里没有Redis，开通后填内网地址
REDIS_PASSWORD=TODO
JWT_ACCESS_SECRET=$JWT_ACCESS
JWT_REFRESH_SECRET=$JWT_REFRESH
AI_API_KEY=$AI_KEY
AI_BASE_URL=$AI_BASE
AI_MODEL=$AI_MODEL
OSS_ENDPOINT=$OSS_ENDPOINT
OSS_ACCESS_KEY_ID=$OSS_AK
OSS_ACCESS_KEY_SECRET=$OSS_SK
OSS_BUCKET=$OSS_BUCKET
WECHAT_APPID=$WX_APPID
WECHAT_SECRET=$WX_SECRET
SERVER_PORT=8080
EOF

python3 - "$TEXT" "$JSON" <<'PY'
import json, sys
src, dst = sys.argv[1], sys.argv[2]
env = {}
for line in open(src, encoding='utf-8'):
    line = line.strip()
    if line and '=' in line:
        k, v = line.split('=', 1)
        env[k] = v
json.dump(env, open(dst, 'w', encoding='utf-8'), ensure_ascii=False, indent=2)
PY

echo "已生成两份，直接打开复制："
echo "  $TEXT"
echo "  $JSON"
echo "其中 REDIS_HOST / REDIS_PASSWORD 还是 TODO，要先开通 Redis。"
echo "注意：JWT 两串是新生成的，以后别再改，改了所有已登录用户要重新登录。"
