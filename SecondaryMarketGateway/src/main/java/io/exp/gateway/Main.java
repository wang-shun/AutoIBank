package io.exp.gateway;

import com.google.common.collect.Lists;
import io.exp.gateway.fake.FakeBondMarketGatewayFactory;
import io.exp.gateway.observe.BondTradeAvroFileObserver;
import io.exp.gateway.observe.BondTradeAvroKafkaObserver;
import io.exp.gateway.observe.Observer;
import io.exp.security.model.BondTrade;
import io.exp.security.model.Trade;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.file.CodecFactory;
import org.apache.avro.file.DataFileConstants;
import org.apache.avro.file.DataFileWriter;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.reflect.ReflectDatumWriter;
import org.apache.beam.sdk.coders.AvroCoder;
import org.apache.commons.cli.*;



import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

@Slf4j
public class Main {

    final static Observer<BondTrade> TradeObserverConsoleLogObserver = new Observer<BondTrade>() {
        @Override
        public void update(BondTrade msg) {
            log.debug(msg.toString());
        }

        @Override
        public void throwError(Throwable ex) {
            log.error(ex.getMessage());
        }

        @Override
        public String getDescription() {
            return "Log observer";
        }
    };
    public static Supplier<Options> createOptions = ()->{
        // create the Options
        Options options = new Options();
        options.addOption("o","outFile", true, "Trade output to a file" );
        options.addOption("k","kafkaServerCfg", true, "Kafka Config = <hostname>:<port>" );
        options.addOption("t","kafkaTopic", true, "Kafka Topic" );
        return options;
    };

    public static void main(String []args){
        log.info("Running Gateway");
        AbstractMarketGatewayFactory<BondTrade> marketGatewayFactory = new FakeBondMarketGatewayFactory();
        MarketGatewayInterface<BondTrade> marketGatewayInterface = marketGatewayFactory.createMarketGateway("test", "ABCD");

        Observer<BondTrade> bondTradeAvroFileObserver=null;
        Observer<BondTrade> bondTradeKafkaObserver=null;

        // create the parser
        try {
            CommandLineParser commandLineParser = new DefaultParser();
            CommandLine commandLine = commandLineParser.parse(createOptions.get(), args);
            bondTradeAvroFileObserver = Optional.ofNullable(commandLine.getOptionValue("outFile")).map(
                    fileName->{
                        try {
                            return new BondTradeAvroFileObserver(fileName);
                        }catch (Exception fe){
                            log.error(fe.getMessage());
                            return null;
                        }
                    }
            ).orElse(null);
            String kafkaServerCfg = commandLine.getOptionValue("kafkaServerCfg");
            String kafkaTopic = commandLine.getOptionValue("kafkaTopic");
            if (kafkaServerCfg!=null && kafkaTopic!=null){
                bondTradeKafkaObserver = new BondTradeAvroKafkaObserver(kafkaServerCfg,kafkaTopic);
            }

        }catch(ParseException pe){
            log.error(pe.getMessage());
        }
        List<Observer<BondTrade>> observerList = Lists.newLinkedList();
        observerList.add(TradeObserverConsoleLogObserver);
        if (bondTradeAvroFileObserver!=null)
            observerList.add(bondTradeAvroFileObserver);
        if (bondTradeKafkaObserver!=null)
            observerList.add(bondTradeKafkaObserver);

        marketGatewayInterface.connect();
        MarketRegistry.registerMarket(marketGatewayInterface, observerList);

        while(true){
            try {
                Thread.sleep(5000);
            }catch(InterruptedException ie){}
        }

    }
}
