package com.sunnyblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("blog_site_profile")
public class SiteProfile {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String nickname;
    private String avatar;
    private String bio;
    private String slogan;
    private String location;
    private String email;
    private String github;
    private String aboutContent;
    private String footerText;
    private LocalDateTime updatedAt;
}
