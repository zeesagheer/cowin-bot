package com.zeeshan.cowin.entities;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;

import javax.persistence.*;
import java.util.Date;

@Entity
@Table(name = "RESULT")
@Data
public class CowinResult {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
    private Long centerId;
    @Column(length = 2000)
    private String name;
    private Integer age;
    private String date;
    private Integer dose;
    private Integer dose1;
    private Integer dose2;
    private String vaccine;
    private String pinCode;
    private String sessionId;
    private String searchedPinCode;
    private String searchedDistrictId;
    private String uuid;
    private Boolean isToken;

    @Getter(onMethod = @__({@JsonIgnore}))
    @Column(updatable = false)
    @CreationTimestamp
    @Temporal(TemporalType.TIMESTAMP)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "MM/dd/yyyy HH:mm:ss")
    private Date createdDate;
}
