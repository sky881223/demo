package com.example.demo;

import jakarta.persistence.*;
import lombok.Data; 

@Data
@Entity
@Table(name = "menus")
public class Menu {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;           
    private String photo;          
    private String description;    
    private Integer price;         
    private String provideDate;    
}