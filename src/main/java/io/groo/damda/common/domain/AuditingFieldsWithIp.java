package io.groo.damda.common.domain;

import io.groo.core.domain.AuditingFields;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;

@Getter
@MappedSuperclass
public abstract class AuditingFieldsWithIp extends AuditingFields {
    @Column(name = "create_ip", length = 45, updatable = false)
    private String createIp;

    @Column(name = "modify_ip", length = 45)
    private String modifyIp;

    public void setCreateIp(String ip) {
        this.createIp = ip;
    }

    public void setModifyIp(String ip) {
        this.modifyIp = ip;
    }
}
