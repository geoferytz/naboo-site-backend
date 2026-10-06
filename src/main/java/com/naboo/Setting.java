package com.naboo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity @Table(name="company_settings")
public class Setting {
    @Id @Column(name="setting_key") public String key;
    @Column(name="setting_value") public String value;
    protected Setting() {}
    Setting(String key, String value) { this.key = key; this.value = value; }
}
