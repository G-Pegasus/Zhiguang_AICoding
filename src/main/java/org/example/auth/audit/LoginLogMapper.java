package org.example.auth.audit;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LoginLogMapper {

    void insert(LoginLog log);
}

