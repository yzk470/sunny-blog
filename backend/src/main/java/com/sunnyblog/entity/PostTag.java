package com.sunnyblog.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("blog_post_tag")
public class PostTag {
    private Long postId;
    private Long tagId;
}
