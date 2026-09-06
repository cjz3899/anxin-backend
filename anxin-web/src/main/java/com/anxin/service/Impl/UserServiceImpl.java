package com.anxin.service.impl;

import com.anxin.constant.UploadConstant;
import com.anxin.dto.LoginDTO;
import com.anxin.dto.ProfileDTO;
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
import com.anxin.vo.UserVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;

@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private WxService wxService;

    @Resource
    private FileTypeService fileTypeService;

    @Resource
    private OssStorageService ossStorageService;

    @Resource
    private WxSecurityService wxSecurityService;

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
    public AvatarVO uploadAvatar(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ServiceException(ResultCode.PARAM_ERROR.getCode(), "请选择头像文件");
        }
        // 先按大小拦截，不读流
        if (file.getSize() > UploadConstant.AVATAR_MAX_BYTES) {
            throw new ServiceException(ResultCode.FILE_SIZE_EXCEEDED.getCode(), "头像大小不能超过2MB");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            log.error("读取头像文件失败", e);
            throw new ServiceException(ResultCode.FILE_SAVE_FAILED);
        }
        // 用 Tika 按文件头魔数校验真实类型，防止改名伪装（如 exe 改成 .jpg）
        String mime = fileTypeService.detectMime(bytes);
        if (!fileTypeService.isAvatar(mime)) {
            throw new ServiceException(ResultCode.FILE_TYPE_NOT_SUPPORTED.getCode(),
                    "头像仅支持 BMP/JPEG/JPG/GIF/PNG 格式图片");
        }
        // 内容安全：违规（errcode=87014）在此抛 CONTENT_VIOLATION，文件不会进入 OSS
        wxSecurityService.checkImage(bytes);
        // 1:1 正方形由前端裁剪/展示保证（微信 chooseAvatar 已裁为正方形），后端不强制
        String key = ossStorageService.upload(bytes, mime, "avatars", fileTypeService.realExtOf(mime));
        return AvatarVO.builder()
                .avatar(ossStorageService.toUrl(key))
                .build();
    }
}
