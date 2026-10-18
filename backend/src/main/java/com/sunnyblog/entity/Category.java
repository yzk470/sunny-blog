package com.sunnyblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("blog_category")
public class Category {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String slug;
    private String description;
    private String color;
    private Integer sortOrder;
    private LocalDateTime createdAt;
}
