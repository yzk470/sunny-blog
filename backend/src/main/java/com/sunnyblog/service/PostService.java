package com.sunnyblog.service;

import com.sunnyblog.common.PageResult;
import com.sunnyblog.dto.AdminPostVO;
import com.sunnyblog.dto.ArchiveItemVO;
import com.sunnyblog.dto.PostDetailVO;
import com.sunnyblog.dto.PostSummaryVO;
import com.sunnyblog.dto.request.PostSaveRequest;

import java.util.List;

public interface PostService {

    PageResult<PostSummaryVO> listPublished(int page, int size, String category, String tag, String keyword, String sort);

    List<PostSummaryVO> latest(int limit);

    List<PostSummaryVO> featured(int limit);

    PostDetailVO getPublishedBySlug(String slug);

    List<ArchiveItemVO> archive();

    long recordView(String slug);

    long like(String slug);

    PageResult<PostSummaryVO> adminList(int page, int size, Integer status, String keyword);

    AdminPostVO getAdmin(Long id);

    AdminPostVO create(PostSaveRequest request);

    AdminPostVO update(Long id, PostSaveRequest request);

    void changeStatus(Long id, Integer status);

    void delete(Long id);
}
