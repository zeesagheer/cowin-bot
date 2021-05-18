package com.zeeshan.cowin.service;

import com.zeeshan.cowin.dto.PlanRequest;
import com.zeeshan.cowin.dto.Result;

import java.io.IOException;
import java.util.List;

public interface CowinService {
    List<Result> getPlans(PlanRequest request) throws IOException;
}
