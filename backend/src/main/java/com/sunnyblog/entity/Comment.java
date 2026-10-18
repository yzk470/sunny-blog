package com.sunnyblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("blog_comment")
public class Comment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long postId;
    private String author;
    private String email;
    private String website;
    private String content;
    private Integer status;
    private Boolean isAdmin;
    private Long parentId;
    private LocalDateTime createdAt;
}
