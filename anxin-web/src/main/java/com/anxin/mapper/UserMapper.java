package com.anxin.mapper;

import com.anxin.entity.User;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 用户表数据访问接口（原生 MyBatis + 注解 SQL）。
 */
@Mapper
public interface UserMapper {

    @Select("SELECT * FROM `user` WHERE openid = #{openid} LIMIT 1")
    User selectByOpenid(String openid);

    @Select("SELECT * FROM `user` WHERE id = #{id}")
    User selectById(Long id);

    /** id 由数据库自增生成，@Options 把生成的 id 回填到实体 */
    @Insert("INSERT INTO `user` (openid, nickname, avatar, status, created_time, updated_time) "
            + "VALUES (#{openid}, #{nickname}, #{avatar}, #{status}, #{createdTime}, #{updatedTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(User user);

    @Update("UPDATE `user` SET nickname = #{nickname}, avatar = #{avatar}, updated_time = #{updatedTime} WHERE id = #{id}")
    int updateById(User user);
}
