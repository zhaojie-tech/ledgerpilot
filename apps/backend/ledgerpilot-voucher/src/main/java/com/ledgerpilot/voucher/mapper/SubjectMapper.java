package com.ledgerpilot.voucher.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ledgerpilot.voucher.entity.AccSubject;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SubjectMapper extends BaseMapper<AccSubject> {

    // MyBatis-Plus 租户插件自动注入 tenant_id，无需手动写
    @Select("SELECT * FROM acc_subject WHERE deleted = 0 ORDER BY sys_sort ASC, subject_code ASC")
    List<AccSubject> selectTreeList();

    @Select("SELECT COUNT(*) > 0 FROM acc_subject WHERE parent_code = #{subjectCode} AND deleted = 0 LIMIT 1")
    boolean hasChildren(@Param("subjectCode") String subjectCode);

    @Insert("<script>" +
        "INSERT INTO acc_subject (tenant_id, subject_code, subject_name, subject_type, level, parent_code, direction, is_detail, is_enabled, sys_sort, created_at, updated_at) " +
        "SELECT #{tenantId}, subject_code, subject_name, subject_type, level, parent_code, direction, is_detail, 1, sys_sort, NOW(), NOW() " +
        "FROM acc_subject_system WHERE system_code = #{systemCode}" +
        "</script>")
    void insertBatchFromTemplate(@Param("tenantId") Long tenantId, @Param("systemCode") String systemCode);
}
