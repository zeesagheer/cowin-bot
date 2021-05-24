package com.zeeshan.cowin.repositories;

import com.zeeshan.cowin.entities.CowinResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CowinResultsRepo extends JpaRepository<CowinResult, Long> {
}
