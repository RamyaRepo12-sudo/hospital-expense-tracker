package com.hospital.expense.security;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import java.util.Date;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Update;

/** Counters in MongoDB (atomic upsert + TTL index), so lockouts and rate limits hold across several instances. */
public class MongoRateStore implements RateStore {
    @Document("rate_limits")
    public static class Bucket {
        @Id public String id;
        public long count;
        @Indexed(expireAfterSeconds = 0) public Date expireAt;
    }

    private final MongoTemplate template;

    public MongoRateStore(MongoTemplate template) { this.template = template; }

    private static String id(String key, long windowMs) { return key + "#" + System.currentTimeMillis() / windowMs; }

    @Override
    public long hit(String key, long windowMs) {
        long w = System.currentTimeMillis() / windowMs;
        Update u = new Update().inc("count", 1).setOnInsert("expireAt", new Date((w + 1) * windowMs + 60_000));
        Bucket b = template.findAndModify(query(where("_id").is(key + "#" + w)), u, FindAndModifyOptions.options().upsert(true).returnNew(true), Bucket.class);
        return b == null ? 1 : b.count;
    }

    @Override
    public long count(String key, long windowMs) {
        Bucket b = template.findById(id(key, windowMs), Bucket.class);
        return b == null ? 0 : b.count;
    }

    @Override
    public void clear(String key, long windowMs) { template.remove(query(where("_id").is(id(key, windowMs))), Bucket.class); }
}
