package org.ticketstampede.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sale_version")
public class SaleVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(nullable = false, updatable = false)
    private int capacity;

    @Column(nullable = false)
    private boolean active;

    public SaleVersion()
    {}

    public SaleVersion(int capacity)
    {
        this.capacity = capacity;
        this.active = false;
    }

    public void activate()
    {
        this.active = true;
        this.startedAt = Instant.now();
    }

    public void deactivate()
    {
        this.active = false;
        this.endedAt = Instant.now();
    }

    public UUID getId(){ return id;}
    public int getCapacity(){ return capacity;}
    public Instant getStartedAt(){ return startedAt;}
    public Instant getEndedAt(){ return endedAt;}
    public boolean isActive(){ return active;}

}
