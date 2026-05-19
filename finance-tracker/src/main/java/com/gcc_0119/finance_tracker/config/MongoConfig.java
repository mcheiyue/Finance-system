package com.gcc_0119.finance_tracker.config;

import com.gcc_0119.finance_tracker.model.MonthlyReport;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexInfo;

import java.util.List;

@Configuration
public class MongoConfig {

    @Bean
    MongoTransactionManager transactionManager(MongoDatabaseFactory dbFactory) {
        return new MongoTransactionManager(dbFactory);
    }

    @Bean
    public org.springframework.boot.ApplicationRunner ensureIndexes(MongoTemplate mongoTemplate) {
        return args -> {
            List<IndexInfo> existingIndexes = mongoTemplate.indexOps(MonthlyReport.class).getIndexInfo();
            for (IndexInfo info : existingIndexes) {
                if (!"_id_".equals(info.getName())) {
                    try {
                        mongoTemplate.indexOps(MonthlyReport.class).dropIndex(info.getName());
                    } catch (Exception ignored) {
                    }
                }
            }
            mongoTemplate.indexOps(MonthlyReport.class)
                    .ensureIndex(new Index().on("userId", Sort.Direction.ASC).on("month", Sort.Direction.ASC).unique());
        };
    }
}
