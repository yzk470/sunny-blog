package com.sunnyblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("blog_user")
public class BlogUser {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String passwordHash;
    private String nickname;
    private String avatar;
    private String role;
    private LocalDateTime createdAt;
}
