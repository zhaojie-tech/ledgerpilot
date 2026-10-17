package com.ledgerpilot.voucher.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ledgerpilot.common.exception.LedgerPilotException;
import com.ledgerpilot.voucher.dto.SubjectCreateRequest;
import com.ledgerpilot.voucher.dto.SubjectUpdateRequest;
import com.ledgerpilot.voucher.entity.AccSubject;
import com.ledgerpilot.voucher.mapper.SubjectMapper;
import com.ledgerpilot.voucher.service.SubjectService;
import com.ledgerpilot.voucher.vo.SubjectVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class SubjectServiceImpl implements SubjectService {

    private final SubjectMapper subjectMapper;

    @Override
    public List<SubjectVO> getTree() {
        List<AccSubject> all = subjectMapper.selectTreeList();
        return buildTree(all);
    }

    @Override
    public SubjectVO getById(Long id) {
        AccSubject entity = subjectMapper.selectOne(
            new LambdaQueryWrapper<AccSubject>()
                .eq(AccSubject::getId, id)
                .eq(AccSubject::getDeleted, 0)
        );
        if (entity == null) {
            throw new LedgerPilotException("科目不存在");
        }
        return toVO(entity);
    }

    @Override
    @Transactional
    public SubjectVO create(SubjectCreateRequest req) {
        // 检查编码唯一性（MyBatis-Plus 租户插件自动加 tenant_id 条件）
        long exists = subjectMapper.selectCount(
            new LambdaQueryWrapper<AccSubject>()
                .eq(AccSubject::getSubjectCode, req.getSubjectCode())
                .eq(AccSubject::getDeleted, 0)
        );
        if (exists > 0) {
            throw new LedgerPilotException("科目编码已存在");
        }

        // 计算级次
        int level = 1;
        if (req.getParentCode() != null && !req.getParentCode().isEmpty()) {
            AccSubject parent = subjectMapper.selectOne(
                new LambdaQueryWrapper<AccSubject>()
                    .eq(AccSubject::getSubjectCode, req.getParentCode())
                    .eq(AccSubject::getDeleted, 0)
            );
            if (parent == null) {
                throw new LedgerPilotException("父级科目不存在");
            }
            level = parent.getLevel() + 1;
        }

        AccSubject entity = new AccSubject();
        // tenant_id 由 MyBatis-Plus 自动注入
        entity.setSubjectCode(req.getSubjectCode());
        entity.setSubjectName(req.getSubjectName());
        entity.setSubjectType(req.getSubjectType());
        entity.setLevel(level);
        entity.setParentCode(req.getParentCode());
        entity.setDirection(req.getDirection());
        entity.setIsDetail(req.getIsDetail() != null ? req.getIsDetail() : 0);
        entity.setAuxDimensions(req.getAuxDimensions());
        entity.setIsEnabled(1);
        entity.setSysSort(0);
        subjectMapper.insert(entity);

        return toVO(entity);
    }

    @Override
    @Transactional
    public SubjectVO update(SubjectUpdateRequest req) {
        AccSubject entity = subjectMapper.selectOne(
            new LambdaQueryWrapper<AccSubject>()
                .eq(AccSubject::getId, req.getId())
                .eq(AccSubject::getDeleted, 0)
        );
        if (entity == null) {
            throw new LedgerPilotException("科目不存在");
        }

        if (req.getSubjectName() != null) {
            entity.setSubjectName(req.getSubjectName());
        }
        if (req.getSubjectType() != null) {
            entity.setSubjectType(req.getSubjectType());
        }
        if (req.getAuxDimensions() != null) {
            entity.setAuxDimensions(req.getAuxDimensions());
        }
        if (req.getIsEnabled() != null) {
            entity.setIsEnabled(req.getIsEnabled());
        }
        if (req.getSysSort() != null) {
            entity.setSysSort(req.getSysSort());
        }

        subjectMapper.updateById(entity);
        return toVO(entity);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        AccSubject entity = subjectMapper.selectOne(
            new LambdaQueryWrapper<AccSubject>()
                .eq(AccSubject::getId, id)
                .eq(AccSubject::getDeleted, 0)
        );
        if (entity == null) {
            throw new LedgerPilotException("科目不存在");
        }

        if (subjectMapper.hasChildren(entity.getSubjectCode())) {
            throw new LedgerPilotException("该科目下存在子科目，无法删除");
        }

        subjectMapper.deleteById(id);
    }

    @Override
    @Transactional
    public void initFromTemplate(String systemCode) {
        long count = subjectMapper.selectCount(
            new LambdaQueryWrapper<AccSubject>().eq(AccSubject::getDeleted, 0)
        );
        if (count > 0) {
            throw new LedgerPilotException("租户已存在科目，请勿重复初始化");
        }
        // insertBatchFromTemplate 直接用注解 SQL，需要 tenantId
        // 从 ThreadLocal 获取（由 TenantInterceptor 注入）
        Long tenantId = com.ledgerpilot.common.util.TenantContext.get();
        subjectMapper.insertBatchFromTemplate(tenantId, systemCode);
    }

    @Override
    @Transactional
    public void toggleEnabled(Long id) {
        AccSubject entity = subjectMapper.selectOne(
            new LambdaQueryWrapper<AccSubject>()
                .eq(AccSubject::getId, id)
                .eq(AccSubject::getDeleted, 0)
        );
        if (entity == null) {
            throw new LedgerPilotException("科目不存在");
        }
        entity.setIsEnabled(entity.getIsEnabled() == 1 ? 0 : 1);
        subjectMapper.updateById(entity);
    }

    // ---- private helpers ----

    private List<SubjectVO> buildTree(List<AccSubject> all) {
        if (all == null || all.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, SubjectVO> map = new LinkedHashMap<>();
        for (AccSubject a : all) {
            SubjectVO vo = toVO(a);
            vo.setChildren(new ArrayList<>());
            map.put(a.getSubjectCode(), vo);
        }

        List<SubjectVO> roots = new ArrayList<>();
        for (AccSubject a : all) {
            SubjectVO vo = map.get(a.getSubjectCode());
            if (a.getParentCode() == null || a.getParentCode().isEmpty() || !map.containsKey(a.getParentCode())) {
                roots.add(vo);
            } else {
                SubjectVO parent = map.get(a.getParentCode());
                if (parent != null) {
                    parent.getChildren().add(vo);
                }
            }
        }
        return roots;
    }

    private SubjectVO toVO(AccSubject entity) {
        SubjectVO vo = new SubjectVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
