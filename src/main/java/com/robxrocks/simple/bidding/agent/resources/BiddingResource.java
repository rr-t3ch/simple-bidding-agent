package com.robxrocks.simple.bidding.agent.resources;

import com.robxrocks.simple.bidding.agent.api.BiddingRequest;
import com.robxrocks.simple.bidding.agent.api.BiddingResponse;
import com.robxrocks.simple.bidding.agent.db.CoefficientDao;
import com.robxrocks.simple.bidding.agent.util.BiddingHelper;
import com.robxrocks.simple.bidding.agent.util.PredictionCalculator;
import io.swagger.annotations.*;
import lombok.Builder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Path("/")
@Api("/")
@Produces(APPLICATION_JSON)
@Builder
public class BiddingResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(BiddingResource.class);

    @NotNull
    private CoefficientDao coefficientDao;

    @NotNull
    private BiddingHelper biddingHelper;

    @NotNull
    private PredictionCalculator predictionCalculator;

    private final String REDIS_FIELD_NAME = "model";
    private final String DEVICE_EXT_BROWSER_KEY = "deviceExtBrowser";
    private final String BANNER_EXT_SIZE_KEY = "bannerExtSize";
    private final String DEVICE_LANGUAGE_KEY = "deviceLanguage";
    private final String DEVICE_EXT_TYPE = "deviceExtType";
    private final String BIAS = "bias";

    @POST
    @Consumes(APPLICATION_JSON)
    @ApiOperation(value = "Predicts a click through rate (CTR)", notes = "Predicts a click through rate for given features")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Valid response - CTR returned"),
            @ApiResponse(code = 400, message = "Invalid request"),
            @ApiResponse(code = 500, message = "Internal Server Error")
    })
    public Response predictCTR(@ApiParam(value = "Bidding Request", required = true)
                               @Valid final BiddingRequest biddingRequest) {
        BiddingResponse response;

        try {
            Map<String, String> requestParams = new HashMap<>();
            requestParams.put(DEVICE_EXT_BROWSER_KEY, biddingRequest.getDeviceExtBrowser());
            requestParams.put(BANNER_EXT_SIZE_KEY, biddingRequest.getBannerExtSize());
            requestParams.put(DEVICE_LANGUAGE_KEY, biddingRequest.getDeviceLanguage());
            requestParams.put(DEVICE_EXT_TYPE, biddingRequest.getDeviceExtType());

            Map<String, String> coefficients = new HashMap<>();
            requestParams.forEach((key, value) ->
                coefficients.put(key, coefficientDao.hget(REDIS_FIELD_NAME, key + "=" + value))
            );
            coefficients.put(BIAS, coefficientDao.hget(REDIS_FIELD_NAME, BIAS));

            List<Double> convertedCoefficients = biddingHelper.convertCoefficients(coefficients);

            String ctrPrediction = predictionCalculator.calculateCTR(convertedCoefficients).toString();
            response = BiddingResponse.builder().ctr(ctrPrediction).build();

        } catch (Exception ex) {
            LOGGER.error("CTR could not be predicted due to unexpexted exception: {}", ex.getMessage());
            if (ex instanceof IllegalArgumentException) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            } else {
                throw ex;
            }
        }

        return Response.ok(response).build();
    }

}
