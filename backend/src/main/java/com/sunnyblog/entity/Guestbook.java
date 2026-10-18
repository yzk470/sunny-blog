package com.sunnyblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("blog_guestbook")
public class Guestbook {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String author;
    private String email;
    private String content;
    private String reply;
    private Integer status;
    private LocalDateTime createdAt;
}
