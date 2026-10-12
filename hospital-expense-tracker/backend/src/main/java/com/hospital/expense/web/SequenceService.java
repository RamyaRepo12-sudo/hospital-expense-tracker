package com.hospital.expense.web;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import com.hospital.expense.model.Counter;
import com.hospital.expense.model.Expense;
import com.hospital.expense.repo.ExpenseRepository;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

/** Atomic counters in MongoDB, so expense numbers stay unique across deletions and across several instances. */
@Service
public class SequenceService {
    private static final Pattern NUMBER = Pattern.compile("EXP-(\\d+)");

    private final MongoTemplate template;
    private final ExpenseRepository expenses;
    private final Set<String> ready = new HashSet<>();

    public SequenceService(MongoTemplate template, ExpenseRepository expenses) { this.template = template; this.expenses = expenses; }

    /** Next number for "expense": 1001, 1002, ... It never goes below the highest EXP- number that already exists. */
    public long next(String name) {
        synchronized (ready) {
            if (ready.add(name) && "expense".equals(name)) {
                long max = 1000;
                for (Expense e : expenses.findAll()) {
                    Matcher m = e.expenseNo == null ? null : NUMBER.matcher(e.expenseNo);
                    if (m != null && m.matches()) max = Math.max(max, Long.parseLong(m.group(1)));
                }
                template.upsert(query(where("_id").is(name)), new Update().max("seq", max), Counter.class);
            }
        }
        Counter c = template.findAndModify(query(where("_id").is(name)), new Update().inc("seq", 1),
                FindAndModifyOptions.options().upsert(true).returnNew(true), Counter.class);
        return c == null ? 1 : c.seq;
    }
}
