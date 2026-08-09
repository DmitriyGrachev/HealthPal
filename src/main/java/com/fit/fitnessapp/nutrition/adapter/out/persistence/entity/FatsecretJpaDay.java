package com.fit.fitnessapp.nutrition.adapter.out.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "fatsecret_day",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "date"}))
public class FatsecretJpaDay {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "fatsecret_day_seq")
    @SequenceGenerator(name = "fatsecret_day_seq", sequenceName = "fatsecret_day_id_seq", allocationSize = 50)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private LocalDate date;

    @Column(name = "date_int")
    private int dateInt;

    private double calories;
    private double protein;
    private double fat;
    private double carbohydrate;

    @OneToMany(mappedBy = "day", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<FatsecretFoodEntry> entries = new ArrayList<>();

    @Version
    private Long version;

    @Column(name = "external_hash")
    private String externalHash;

    @Column(name = "summary_hash")
    private String summaryHash;

    @Column(name = "entries_hash")
    private String entriesHash;

    @Column(name = "last_sync_at")
    private Instant lastSyncAt;

    public FatsecretJpaDay() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public int getDateInt() { return dateInt; }
    public void setDateInt(int dateInt) { this.dateInt = dateInt; }

    public double getCalories() { return calories; }
    public void setCalories(double calories) { this.calories = calories; }

    public double getProtein() { return protein; }
    public void setProtein(double protein) { this.protein = protein; }

    public double getFat() { return fat; }
    public void setFat(double fat) { this.fat = fat; }

    public double getCarbohydrate() { return carbohydrate; }
    public void setCarbohydrate(double carbohydrate) { this.carbohydrate = carbohydrate; }

    public List<FatsecretFoodEntry> getEntries() { return entries; }
    public void setEntries(List<FatsecretFoodEntry> entries) { this.entries = entries; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public String getExternalHash() { return externalHash; }
    public void setExternalHash(String externalHash) { this.externalHash = externalHash; }

    public String getSummaryHash() { return summaryHash; }
    public void setSummaryHash(String summaryHash) { this.summaryHash = summaryHash; }

    public String getEntriesHash() { return entriesHash; }
    public void setEntriesHash(String entriesHash) { this.entriesHash = entriesHash; }

    public Instant getLastSyncAt() { return lastSyncAt; }
    public void setLastSyncAt(Instant lastSyncAt) { this.lastSyncAt = lastSyncAt; }

    public void addEntry(FatsecretFoodEntry entry) {
        entries.add(entry);
        entry.setDay(this);
    }

    public void removeEntry(FatsecretFoodEntry entry) {
        entries.remove(entry);
        entry.setDay(null);
    }
}
