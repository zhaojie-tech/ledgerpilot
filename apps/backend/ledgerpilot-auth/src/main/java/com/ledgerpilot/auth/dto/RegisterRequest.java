package com.ledgerpilot.auth.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/**
 * 注册请求DTO
 */
@Data
public class RegisterRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 50, message = "用户名长度为3-50位")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 50, message = "密码长度为6-50位")
    private String password;

    /** 公司/店铺名称 */
    @NotBlank(message = "公司名称不能为空")
    @Size(max = 200, message = "公司名称最多200字")
    private String companyName;

    /** 纳税人类型：GENERAL=一般纳税人, SMALL_SCALE=小规模纳税人 */
    @NotBlank(message = "纳税人类型不能为空")
    @Pattern(regexp = "GENERAL|SMALL_SCALE", message = "纳税人类型必须是GENERAL或SMALL_SCALE")
    private String taxpayerType;

    /** 行业：TRADING=商贸, SERVICE=服务, MANUFACTURING=制造 */
    @Pattern(regexp = "TRADING|SERVICE|MANUFACTURING|", message = "行业类型无效")
    private String industry;
}
