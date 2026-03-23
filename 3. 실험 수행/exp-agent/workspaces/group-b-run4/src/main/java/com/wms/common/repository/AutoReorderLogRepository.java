package com.wms.common.repository;

import com.wms.common.entity.AutoReorderLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface AutoReorderLogRepository extends JpaRepository<AutoReorderLog, UUID> {
}
