package com.hospital.expense.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/** Master data managed by admins: DEPARTMENT, CATEGORY or VENDOR. "extra" holds e.g. a vendor's GSTIN or a department head. */
@Document("lookups")
@CompoundIndex(def = "{'type': 1, 'name': 1}", unique = true)
public class Lookup {
    @Id public String id;
    public String type, name, extra;
    public boolean active = true;

    public Lookup() {}
    public Lookup(String type, String name, String extra) { this.type = type; this.name = name; this.extra = extra; }
}
