package com.sunnyblog.service;

import com.sunnyblog.common.PageResult;
import com.sunnyblog.dto.GuestbookVO;
import com.sunnyblog.dto.request.GuestbookCreateRequest;

public interface GuestbookService {

    PageResult<GuestbookVO> listApproved(int page, int size);

    GuestbookVO create(GuestbookCreateRequest request);

    PageResult<GuestbookVO> adminList(Integer status, int page, int size);

    void moderate(Long id, int status);

    void reply(Long id, String reply);

    void delete(Long id);
}
