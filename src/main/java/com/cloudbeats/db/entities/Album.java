package com.cloudbeats.db.entities;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.util.UUID;

@Entity
@Table(name = "albums", uniqueConstraints = {
        @UniqueConstraint(name = "uniq_album_per_user", columnNames = {"name", "user_id"})
})
public class Album {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ApplicationUser user;

    public UUID getId()                        { return id; }

    public String getName()                    { return name; }
    public void   setName(String name)         { this.name = name; }

    public ApplicationUser getUser()           { return user; }
    public void setUser(ApplicationUser user)  { this.user = user; }
}
