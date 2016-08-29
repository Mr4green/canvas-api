package edu.ksu.canvas.impl;

import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import edu.ksu.canvas.enums.AssignmentType;
import edu.ksu.canvas.exception.InvalidOauthTokenException;
import edu.ksu.canvas.exception.OauthTokenRequiredException;
import edu.ksu.canvas.interfaces.AssignmentReader;
import edu.ksu.canvas.interfaces.AssignmentWriter;
import edu.ksu.canvas.model.Assignment;
import edu.ksu.canvas.model.Delete;
import edu.ksu.canvas.net.Response;
import edu.ksu.canvas.net.RestClient;
import edu.ksu.canvas.requestOptions.GetSingleAssignmentOptions;
import edu.ksu.canvas.requestOptions.ListCourseAssignmentsOptions;
import edu.ksu.canvas.requestOptions.ListUserAssignmentOptions;
import edu.ksu.canvas.exception.MessageUndeliverableException;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class AssignmentImpl extends BaseImpl<Assignment, AssignmentReader, AssignmentWriter> implements AssignmentReader, AssignmentWriter{
    private static final Logger LOG = Logger.getLogger(AssignmentReader.class);

    public AssignmentImpl(String canvasBaseUrl, Integer apiVersion, String oauthToken, RestClient restClient, int connectTimeout, int readTimeout, Integer paginationPageSize) {
        super(canvasBaseUrl, apiVersion, oauthToken, restClient, connectTimeout, readTimeout, paginationPageSize);
    }

    @Override
    public List<Assignment> listCourseAssignments(ListCourseAssignmentsOptions options) throws IOException {
        String url = buildCanvasUrl("courses/" + options.getCourseId() + "/assignments" , options.getOptionsMap());
        return getListFromCanvas(url);
    }

    public List<Assignment> listUserAssignments(ListUserAssignmentOptions options) throws IOException {
        String url = buildCanvasUrl("users/" + options.getUserId() + "/courses/" + options.getCourseId() + "/assignments", options.getOptionsMap());
        return getListFromCanvas(url);
    }

    @Override
    public Optional<Assignment> getSingleAssignment(GetSingleAssignmentOptions options) throws IOException {
        String url = buildCanvasUrl("courses/" + options.getCourseId() + "/assignments/" + options.getAssignmentId(), options.getOptionsMap());
        Response response = canvasMessenger.getSingleResponseFromCanvas(oauthToken, url);
        return responseParser.parseToObject(Assignment.class, response);
    }

    @Override
    public Optional<Assignment> createAssignment(String courseId, String assignmentName, String pointsPossible) throws IOException {
        return createAssignment(courseId, assignmentName, pointsPossible,
                AssignmentType.ON_PAPER, true, true);
    }

    @Override
    public Optional<Assignment> createAssignment(String courseId, String assignmentName, String pointsPossible,
                                                 AssignmentType assignmentType, boolean published, boolean muted) throws IOException {
        ImmutableMap<String, List<String>> parameters = ImmutableMap.<String,List<String>>builder()
                .put("assignment[name]", Collections.singletonList(assignmentName))
                .put("assignment[submission_types]", Collections.singletonList(assignmentType.toString()))
                .put("assignment[points_possible]", Collections.singletonList(pointsPossible))
                .put("assignment[published]", Collections.singletonList(String.valueOf(published)))
                .put("assignment[muted]", Collections.singletonList(String.valueOf(muted))).build();
        String url = buildCanvasUrl("courses/" + courseId + "/assignments", parameters);
        Response response = canvasMessenger.sendToCanvas(oauthToken, url, Collections.emptyMap());
        if (response.getErrorHappened() || response.getResponseCode() != 200) {
            LOG.error("Errors creating assignment for course " + courseId + " with assignmentName " + assignmentName);
            return Optional.empty();
        }
        return responseParser.parseToObject(Assignment.class, response);
    }

    @Override
    public Boolean deleteAssignment(String courseId, String assignmentId) throws InvalidOauthTokenException, IOException {
        Map<String, String> postParams = new HashMap<>();
        postParams.put("event", "delete");
        String createdUrl = buildCanvasUrl("courses/" + courseId + "/assignments/" + assignmentId, Collections.emptyMap());
        Response response = canvasMessenger.deleteFromCanvas(oauthToken, createdUrl, postParams);
        LOG.debug("response " + response.toString());
        if(response.getErrorHappened() || response.getResponseCode() != 200){
            LOG.debug("Failed to delete assignment, error message: " + response.toString());
            return false;
        }
        Optional<Delete> responseParsed = responseParser.parseToObject(Delete.class, response);
        return responseParsed.get().getDelete();
    }

    @Override
    public Optional<Assignment> setOnlyVisibleToOverrides(String courseId, String assignmentId, boolean onlyVisibleToOverrides)
            throws MessageUndeliverableException, IOException, OauthTokenRequiredException{
        String url = buildCanvasUrl("courses/" + courseId + "/assignments/" + assignmentId, Collections.emptyMap());
        JsonObject requestBody = new JsonObject();
        JsonObject assignment = new JsonObject();
        assignment.addProperty("only_visible_to_overrides", onlyVisibleToOverrides);
        requestBody.add("assignment", assignment);

        Response response = canvasMessenger.sendJsonPostToCanvas(oauthToken, url, requestBody);
        if(response.getErrorHappened() || response.getResponseCode() != 201){
            LOG.error("Error updating assignment override for course: " + courseId + " and assignment: " + assignmentId);
            LOG.debug(response.getContent());
            return null;
        }
        return responseParser.parseToObject(Assignment.class, response);
    }

    @Override
    protected Type listType() {
        return new TypeToken<List<Assignment>>(){}.getType();
    }

    @Override
    protected Class<Assignment> objectType() {
        return Assignment.class;
    }

}