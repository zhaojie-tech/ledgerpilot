package com.ledgerpilot.voucher.service;

import com.ledgerpilot.voucher.dto.SubjectCreateRequest;
import com.ledgerpilot.voucher.dto.SubjectUpdateRequest;
import com.ledgerpilot.voucher.vo.SubjectVO;

import java.util.List;

public interface SubjectService {

    /** 获取科目树（所有科目，按层级嵌套） */
    List<SubjectVO> getTree();

    /** 根据ID获取科目 */
    SubjectVO getById(Long id);

    /** 创建科目 */
    SubjectVO create(SubjectCreateRequest req);

    /** 更新科目 */
    SubjectVO update(SubjectUpdateRequest req);

    /** 删除科目（只能删除末级且无余额的科目） */
    void delete(Long id);

    /** 从模板初始化科目（把小企业会计准则复制到租户科目表） */
    void initFromTemplate(String systemCode);

    /** 启用/停用切换 */
    void toggleEnabled(Long id);
}
