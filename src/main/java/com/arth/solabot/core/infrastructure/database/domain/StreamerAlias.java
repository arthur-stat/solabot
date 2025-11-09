package com.arth.solabot.core.infrastructure.database.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 
 * @TableName t_streamer_alias
 */
@TableName(value ="t_streamer_alias")
@Data
public class StreamerAlias {
    /**
     * 
     */
    @TableId
    private Long id;

    /**
     * 
     */
    private Long streamerId;

    /**
     * 
     */
    private Long streamId;

    /**
     * 
     */
    private String alias;

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
        StreamerAlias other = (StreamerAlias) that;
        return (this.getId() == null ? other.getId() == null : this.getId().equals(other.getId()))
            && (this.getStreamerId() == null ? other.getStreamerId() == null : this.getStreamerId().equals(other.getStreamerId()))
            && (this.getStreamId() == null ? other.getStreamId() == null : this.getStreamId().equals(other.getStreamId()))
            && (this.getAlias() == null ? other.getAlias() == null : this.getAlias().equals(other.getAlias()))
            && (this.getCreatedAt() == null ? other.getCreatedAt() == null : this.getCreatedAt().equals(other.getCreatedAt()))
            && (this.getUpdatedAt() == null ? other.getUpdatedAt() == null : this.getUpdatedAt().equals(other.getUpdatedAt()));
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((getId() == null) ? 0 : getId().hashCode());
        result = prime * result + ((getStreamerId() == null) ? 0 : getStreamerId().hashCode());
        result = prime * result + ((getStreamId() == null) ? 0 : getStreamId().hashCode());
        result = prime * result + ((getAlias() == null) ? 0 : getAlias().hashCode());
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
        sb.append(", streamerId=").append(streamerId);
        sb.append(", streamId=").append(streamId);
        sb.append(", alias=").append(alias);
        sb.append(", createdAt=").append(createdAt);
        sb.append(", updatedAt=").append(updatedAt);
        sb.append("]");
        return sb.toString();
    }
}