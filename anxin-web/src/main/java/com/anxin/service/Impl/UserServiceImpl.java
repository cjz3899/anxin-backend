package com.anxin.service.impl;

import com.anxin.constant.RedisKeyConstant;
import com.anxin.constant.UploadConstant;
import com.anxin.dto.LoginDTO;
import com.anxin.dto.ProfileDTO;
import com.anxin.dto.UploadConfirmDTO;
import com.anxin.dto.UploadCredentialDTO;
import com.anxin.entity.User;
import com.anxin.enums.ResultCode;
import com.anxin.exception.ServiceException;
import com.anxin.mapper.UserMapper;
import com.anxin.service.IUserService;
import com.anxin.service.support.FileTypeService;
import com.anxin.service.support.OssStorageService;
import com.anxin.service.support.WxSecurityService;
import com.anxin.service.support.WxService;
import com.anxin.threadlocal.BaseContext;
import com.anxin.vo.AvatarVO;
import com.anxin.vo.UploadCredentialVO;
import com.anxin.vo.UserVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Set;

@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    /**
     * 头像对象前缀，签发与确认都按 前缀/用户ID/ 校验归属
     */
    private static final String AVATAR_CATEGORY = "avatars";

    private static final Set<String> AVATAR_EXTS = Set.of("bmp", "jpg", "jpeg", "png", "gif");

    @Resource
    private WxService wxService;

    @Resource
    private FileTypeService fileTypeService;

    @Resource
    private OssStorageService ossStorageService;

    @Resource
    private WxSecurityService wxSecurityService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public User wxlogin(LoginDTO dto) {
        String openid = wxService.code2Session(dto.getCode());

        User user = getOne(new LambdaQueryWrapper<User>().eq(User::getOpenid, openid));

        if (user == null) {
            user = new User();
            user.setOpenid(openid);
            try {
                save(user);
            } catch (DuplicateKeyException e) {
                user = getOne(new LambdaQueryWrapper<User>().eq(User::getOpenid, openid));
            }
        }
        return user;
    }

    private User updateProfile(Long id, String nickname, String avatar) {
        User user = getById(id);
        if (user == null) {
            throw new ServiceException(ResultCode.USER_NOT_EXIST);
        }
        if (nickname != null) {
            user.setNickname(nickname);
        }
        if (avatar != null) {
            user.setAvatar(avatar);
        }
        user.setUpdatedTime(LocalDateTime.now());
        updateById(user);
        return user;
    }

    @Override
    public UserVO me() {
        User user = getById(BaseContext.getCurrentId());
        if (user == null) {
            throw new ServiceException(ResultCode.USER_NOT_EXIST);
        }
        return UserVO.builder()
                .id(user.getId())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .build();
    }

    @Override
    public UserVO profile(ProfileDTO dto) {
        Long userId = BaseContext.getCurrentId();
        User user = updateProfile(userId, dto.getNickname(), dto.getAvatar());
        return UserVO.builder()
                .id(user.getId())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .build();
    }

    @Override
    public UploadCredentialVO requestAvatarCredential(UploadCredentialDTO dto) {
        String ext = fileTypeService.extensionOf(dto.getFileName());
        if (!AVATAR_EXTS.contains(ext)) {
            throw new ServiceException(ResultCode.FILE_TYPE_NOT_SUPPORTED.getCode(),
                    "头像仅支持 BMP/JPEG/JPG/GIF/PNG 格式图片");
        }
        //对象名带当前用户目录，确认时据此拒绝回填别人的 key
        String key = ossStorageService.buildKey(AVATAR_CATEGORY,
                String.valueOf(BaseContext.getCurrentId()), ext);
        return ossStorageService.createPostCredential(key,
                UploadConstant.AVATAR_MAX_BYTES, UploadConstant.CREDENTIAL_TTL);
    }

    @Override
    public AvatarVO confirmAvatarUpload(UploadConfirmDTO dto) {
        String key = dto.getObjectKey();
        String ownPrefix = AVATAR_CATEGORY + "/" + BaseContext.getCurrentId() + "/";
        if (!key.startsWith(ownPrefix) || key.contains("..")) {
            log.warn("直传确认拒绝了非本人前缀的头像 key : {}", key);
            throw new ServiceException(ResultCode.PARAM_ERROR.getCode(), "objectKey 不合法");
        }
        String fileUrl = ossStorageService.toUrl(key);

        try {
            if (ossStorageService.headSize(key) > UploadConstant.AVATAR_MAX_BYTES) {
                throw new ServiceException(ResultCode.FILE_SIZE_EXCEEDED.getCode(), "头像大小不能超过2MB");
            }
            //魔数校验真实类型，防止改名伪装（如 exe 改成 .jpg）
            String mime = fileTypeService.detectMime(
                    ossStorageService.readHead(key, UploadConstant.DETECT_HEAD_BYTES));
            if (!fileTypeService.isAvatar(mime)) {
                throw new ServiceException(ResultCode.FILE_TYPE_NOT_SUPPORTED.getCode(),
                        "头像仅支持 BMP/JPEG/JPG/GIF/PNG 格式图片");
            }
            //内容安全要整份字节，只能把刚直传上来的图拉回来送检；
            //代价是违规图在 OSS 上有一个短暂可读的窗口，检测不过会立即删除，且 key 是随机 UUID 不可猜
            wxSecurityService.checkImage(ossStorageService.download(fileUrl));
        } catch (ServiceException e) {
            //任何一项校验不过都不能把文件留在公共读的 bucket 上
            ossStorageService.deleteByKey(key);
            throw e;
        }

        // 1:1 正方形由前端裁剪/展示保证（微信 chooseAvatar 已裁为正方形），后端不强制
        return AvatarVO.builder().avatar(fileUrl).build();
    }

    @Override
    public void logout(Long userId) {
        stringRedisTemplate.delete(RedisKeyConstant.LOGIN_ACCESS_PREFIX + userId);
        stringRedisTemplate.delete(RedisKeyConstant.LOGIN_REFRESH_PREFIX + userId);
    }
}
