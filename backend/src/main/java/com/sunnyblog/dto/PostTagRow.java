package com.sunnyblog.dto;

import lombok.Data;

@Data
public class PostTagRow {
    private Long postId;
    private Long id;
    private String name;
    private String slug;
    private String color;
}
