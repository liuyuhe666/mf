package cn.com.mfish.oauth.mapper;

import cn.com.mfish.common.oauth.api.entity.SsoTenant;
import cn.com.mfish.oauth.req.ReqSsoTenant;
import cn.com.mfish.common.oauth.api.vo.TenantVo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * @description: 租户信息表
 * @author: mfish
 * @date: 2023-05-31
 * @version: V1.0.1
 */
public interface SsoTenantMapper extends BaseMapper<SsoTenant> {
    List<TenantVo> queryList(@Param("reqSsoTenant") ReqSsoTenant reqSsoTenant);

    /**
     * 是否租户管理员
     *
     * @param userId
     * @param tenantId
     * @return
     */
    @Select("select count(0) from sso_tenant where user_id = #{userId} and id = #{tenantId}")
    int isTenantMaster(@Param("userId") String userId, @Param("tenantId") String tenantId);

}
