package io.exp.analysis.beam.pipeline;

import com.google.gson.Gson;

import io.exp.analysis.beam.pipeline.check.CheckBidAskAvg;
import io.exp.security.model.avro.BidAsk;
import io.exp.security.model.avro.BondTrade;

import lombok.extern.slf4j.Slf4j;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.PipelineResult;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


import java.io.FileNotFoundException;

import java.util.Map;


import static org.assertj.core.api.Assertions.assertThat;


@Slf4j
class BondTradeFileAnalysisPipelineBuilderTest {
    private String [] arguments = null;
    private static final String referenceBidRefJson = "resources/bid.bidAskAvg.json";
    private static final String referenceAskRefJson = "resources/ask.bidAskAvg.json";
    private static Map<String, Double> bidReference = null;
    private static Map<String, Double> askReference = null;

    @BeforeAll
    public static void initAll() throws Exception{
        Gson gson = new Gson();
        //Reader bidReader = new FileReader(referenceBidRefJson);
        //Reader askReader = new FileReader(referenceAskRefJson);
        //bidReference = gson.fromJson(bidReader, Map.class);
        //askReference = gson.fromJson(askReader, Map.class);
    }
    @BeforeEach
    public void init(){
        arguments= new String[]{
                "--fileName=resources/bondmarket.avro"};
    }
    @Test
    void test_avrofile_analysis() throws FileNotFoundException {

        BondTradeAnalysisPipelineBuilderInterface pipelineBuilder= new BondTradeFileAnalysisPipelineBuilder();
        Pipeline pipeline = pipelineBuilder.build(arguments);

        BondTradeRealtimeAnalysisPipelineBuilder.AnalysisProbes analysisProbes = pipelineBuilder.getAnalysisProbes();
        PCollection<BondTrade> pBidTrades = analysisProbes.pBidTrades;
        PCollection<BondTrade> pAskTrades = analysisProbes.pAskTrades;

        pBidTrades.apply("Check Bid Trades", ParDo.of(new CheckBidAskTrade(BidAsk.BID)));
        pAskTrades.apply("Check Ask Trades", ParDo.of(new CheckBidAskTrade(BidAsk.ASK)));

        PCollection<KV<String, Double>> pBidAvgPrice = analysisProbes.pBidAvgPrice;
        PCollection<KV<String, Double>> pAskAvgPrice = analysisProbes.pAskAvgPrice;
        CheckBidAskAvg checkBidAvg = new CheckBidAskAvg(BidAsk.BID);
        CheckBidAskAvg checkAskAvg = new CheckBidAskAvg(BidAsk.ASK);
        pBidAvgPrice.apply("Check Bid Avg Price", ParDo.of(checkBidAvg));
        pAskAvgPrice.apply("Check Ask Avg Price", ParDo.of(checkAskAvg));

        PipelineResult pipelineResult = pipeline.run();
        pipelineResult.waitUntilFinish();
    }
/*
    static class CheckBidAskAvg extends DoFn<KV<String, Double>, Void > {
        private final Map<String, Double> referenceData;
        @Getter
        private Set<String> securitySet = Sets.newHashSet();
        public CheckBidAskAvg(@Nonnull Map<String, Double> referenceData){
            this.referenceData=referenceData;

        }
        @ProcessElement
        public void processElement(@Element KV<String, Double> entry, OutputReceiver<Void> out){
            String SecId = entry.getKey();
            Double Price = entry.getValue();

            assertAll(
                    ()->{
                        assertThat(securitySet.contains(SecId)).isFalse();
                        securitySet.add(SecId);
                    },
                    ()->{
                        assertThat(referenceData.get(SecId)).isNotNull();
                    },
                    ()->{
                        Double price = Optional.of(referenceData.get(SecId)).get();
                        assertThat(price).isCloseTo(Price, Offset.offset(0.0001));
                    }
            );

        }

    }*/


    static class CheckBidAskTrade extends DoFn<BondTrade, Void> {
        BidAsk bidask=null;
        public CheckBidAskTrade(BidAsk bidAsk){
            this.bidask = bidAsk;
        }
        @ProcessElement
        public void processElement(@Element BondTrade bondTrade, OutputReceiver<Void> out) throws Exception {
            //log.debug(bondTrade.toString());
            assertThat(bondTrade.getAsset().getBidask()).isEqualTo(this.bidask);
        }
    }
}