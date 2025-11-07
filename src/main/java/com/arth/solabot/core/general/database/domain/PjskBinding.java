package com.arth.solabot.core.general.database.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 
 * @TableName t_pjsk_binding
 */
@TableName(value ="t_pjsk_binding")
@Data
public class PjskBinding {
    /**
     * 
     */
    @TableId
    private Long id;

    /**
     * 
     */
    private Long userId;

    /**
     * 
     */
    private String cnPjskId;

    /**
     * 
     */
    private String jpPjskId;

    /**
     * 
     */
    private String twPjskId;

    /**
     * 
     */
    private String krPjskId;

    /**
     * 
     */
    private String enPjskId;

    /**
     * 
     */
    private String defaultServerRegion;

    /**
     * 
     */
    private Date createdAt;

    /**
     * 
     */
    private Date updatedAt;

    @Override
    public boolean equals(Object that) {
        if (this == that) {
            return true;
        }
        if (that == null) {
            return false;
        }
        if (getClass() != that.getClass()) {
            return false;
        }
        PjskBinding other = (PjskBinding) that;
        return (this.getId() == null ? other.getId() == null : this.getId().equals(other.getId()))
            && (this.getUserId() == null ? other.getUserId() == null : this.getUserId().equals(other.getUserId()))
            && (this.getCnPjskId() == null ? other.getCnPjskId() == null : this.getCnPjskId().equals(other.getCnPjskId()))
            && (this.getJpPjskId() == null ? other.getJpPjskId() == null : this.getJpPjskId().equals(other.getJpPjskId()))
            && (this.getTwPjskId() == null ? other.getTwPjskId() == null : this.getTwPjskId().equals(other.getTwPjskId()))
            && (this.getKrPjskId() == null ? other.getKrPjskId() == null : this.getKrPjskId().equals(other.getKrPjskId()))
            && (this.getEnPjskId() == null ? other.getEnPjskId() == null : this.getEnPjskId().equals(other.getEnPjskId()))
            && (this.getDefaultServerRegion() == null ? other.getDefaultServerRegion() == null : this.getDefaultServerRegion().equals(other.getDefaultServerRegion()))
            && (this.getCreatedAt() == null ? other.getCreatedAt() == null : this.getCreatedAt().equals(other.getCreatedAt()))
            && (this.getUpdatedAt() == null ? other.getUpdatedAt() == null : this.getUpdatedAt().equals(other.getUpdatedAt()));
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((getId() == null) ? 0 : getId().hashCode());
        result = prime * result + ((getUserId() == null) ? 0 : getUserId().hashCode());
        result = prime * result + ((getCnPjskId() == null) ? 0 : getCnPjskId().hashCode());
        result = prime * result + ((getJpPjskId() == null) ? 0 : getJpPjskId().hashCode());
        result = prime * result + ((getTwPjskId() == null) ? 0 : getTwPjskId().hashCode());
        result = prime * result + ((getKrPjskId() == null) ? 0 : getKrPjskId().hashCode());
        result = prime * result + ((getEnPjskId() == null) ? 0 : getEnPjskId().hashCode());
        result = prime * result + ((getDefaultServerRegion() == null) ? 0 : getDefaultServerRegion().hashCode());
        result = prime * result + ((getCreatedAt() == null) ? 0 : getCreatedAt().hashCode());
        result = prime * result + ((getUpdatedAt() == null) ? 0 : getUpdatedAt().hashCode());
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName());
        sb.append(" [");
        sb.append("Hash = ").append(hashCode());
        sb.append(", id=").append(id);
        sb.append(", userId=").append(userId);
        sb.append(", cnPjskId=").append(cnPjskId);
        sb.append(", jpPjskId=").append(jpPjskId);
        sb.append(", twPjskId=").append(twPjskId);
        sb.append(", krPjskId=").append(krPjskId);
        sb.append(", enPjskId=").append(enPjskId);
        sb.append(", defaultServerRegion=").append(defaultServerRegion);
        sb.append(", createdAt=").append(createdAt);
        sb.append(", updatedAt=").append(updatedAt);
        sb.append("]");
        return sb.toString();
    }
}