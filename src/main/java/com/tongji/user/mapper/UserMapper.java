package com.tongji.user.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.tongji.user.domain.User;

@Mapper
public interface UserMapper {

    User findByPhone(@Param("phone") String phone);

    User findByEmail(@Param("email") String email);

    boolean existsByPhone(@Param("phone") String phone);

    boolean existsByEmail(@Param("email") String email);

    void insert(User user);

    User findById(@Param("id") Long id);

    void updatePassword(@Param("id") Long id, @Param("passwordHash") String passwordHash);

    void updateProfile(User user);

    boolean existsByZgIdExceptId(@Param("zgId") String zgId, @Param("excludeId") Long excludeId);
}
