package com.example.gameflix.model;

// JPA annotations (Entity, Table, Id, Column, ...) that map this class to a database table
import jakarta.persistence.*;

import java.time.Instant;

// @Entity: each User object corresponds to one row in a database table
@Entity
// The table is called "users", and the unique constraint named uk_users_email forces every email
// to be different. The DATABASE enforces this, which is what produces the 409 on a duplicate
@Table(name = "users",
        uniqueConstraints = @UniqueConstraint(name = "uk_users_email", columnNames = "email"))
public class User {

    // Primary key: the column that uniquely identifies each row
    @Id
    // IDENTITY: MySQL generates the id itself (AUTO_INCREMENT), so we never set it
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    // Differences from the Module 3 ERD are explained in the README "Module 3 design-change note"

    // Login name; NOT NULL, up to 255 characters, and unique (see the constraint above)
    @Column(name = "email", nullable = false, length = 255)
    private String email;

    // Column sized for the hash, not the password. VARCHAR(60) would silently truncate.
    // The Java field is passwordHash but the column is named "password" (the assignment's query reads it)
    @Column(name = "password", nullable = false, length = 255)
    private String passwordHash;

    // Name shown for the user; required, up to 50 characters
    @Column(name = "display_name", nullable = false, length = 50)
    private String displayName;

    // When the account was created; filled in automatically when the User object is made
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    // Getters and setters: the way other classes read and change the private fields above
    // (AuthService sets email, password hash and display name; the profile reads them back)
    public long getId() { return id; }

    public void setId(long id) { this.id = id; }

    public String getEmail() { return email; }

    public void setEmail(String email) { this.email = email; }

    public String getPasswordHash() { return passwordHash; }

    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getDisplayName() { return displayName; }

    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public Instant getCreatedAt() { return createdAt; }

    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}