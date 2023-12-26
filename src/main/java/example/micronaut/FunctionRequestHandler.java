package example.micronaut;

import java.net.URISyntaxException;

import org.json.JSONObject;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;

import example.micronaut.domain.FuncRequest;
import example.micronaut.domain.FuncResponse;
import io.micronaut.function.aws.MicronautRequestHandler;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.EventBridgeException;
import software.amazon.awssdk.services.eventbridge.model.PutRuleRequest;
import software.amazon.awssdk.services.eventbridge.model.PutRuleResponse;
import software.amazon.awssdk.services.eventbridge.model.PutTargetsRequest;
import software.amazon.awssdk.services.eventbridge.model.Target;

@Slf4j
public class FunctionRequestHandler
        extends MicronautRequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    @Inject
    private ObjectMapper objectMapper;

    @Override
    public APIGatewayProxyResponseEvent execute(APIGatewayProxyRequestEvent request) {

        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();

        try {
            FuncRequest req = objectMapper.readValue(request.getBody(), FuncRequest.class);
            FuncResponse res = runProcess(req);

            response.setStatusCode(200);
            response.setBody(objectMapper.writeValueAsString(res));

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatusCode(500);
            JSONObject obj = new JSONObject();
            obj.put("error", "error");
            response.setBody(obj.toString());
        }
        return response;
    }

    /**
     * メイン処理
     * 
     * @param request
     * @return
     * @throws URISyntaxException
     */
    private FuncResponse runProcess(FuncRequest request) throws URISyntaxException {

        EventBridgeClient client = EventBridgeClient.builder().build();

        try {
            PutRuleRequest ruleRequest = PutRuleRequest.builder()
                    .name(request.getRuleName())
                    .eventBusName("default")
                    .scheduleExpression("cron(10 06 27 12 ? 2023)")
                    .state("ENABLED")
                    .description("A test rule that runs on a schedule created by the Java API")
                    .build();

            PutRuleResponse ruleResponse = client.putRule(ruleRequest);
            System.out.println("The ARN of the new rule is " + ruleResponse.ruleArn());

            JSONObject input = new JSONObject();
            input.put("key", "value");

            Target target = Target.builder()
                    .id("mn-sample")
                    .arn("arn:aws:lambda:ap-northeast-1:9179129394:function:mn-sample")
                    .input(input.toString())
                    .build();

            PutTargetsRequest targetsRequest = PutTargetsRequest.builder()
                    .rule(request.getRuleName())
                    .targets(target)
                    .eventBusName(null)
                    .build();

            client.putTargets(targetsRequest);

        } catch (EventBridgeException e) {
            log.error(e.awsErrorDetails().errorMessage(), e);
            FuncResponse response = new FuncResponse();
            response.setResult("NG");
            return response;
        }

        FuncResponse response = new FuncResponse();
        response.setResult("OK");
        return response;
    }
}
