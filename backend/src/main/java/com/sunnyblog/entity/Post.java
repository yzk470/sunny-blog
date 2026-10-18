package com.sunnyblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("blog_post")
public class Post {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String slug;
    private String summary;
    private String content;
    private String coverImage;
    private Long categoryId;
    private Integer status;
    private Boolean isTop;
    private Integer viewCount;
    private Integer likeCount;
    private Integer commentCount;
    private Integer readingTime;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime publishedAt;
}
