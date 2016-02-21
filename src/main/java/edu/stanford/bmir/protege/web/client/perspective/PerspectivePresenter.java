package edu.stanford.bmir.protege.web.client.perspective;

import com.google.common.base.Optional;
import com.google.gwt.core.client.GWT;
import com.google.gwt.place.shared.PlaceChangeEvent;
import com.google.gwt.user.client.ui.AcceptsOneWidget;
import com.google.gwt.user.client.ui.Label;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import edu.stanford.bmir.protege.web.client.dispatch.DispatchServiceCallback;
import edu.stanford.bmir.protege.web.client.dispatch.DispatchServiceManager;
import edu.stanford.bmir.protege.web.shared.HasDispose;
import edu.stanford.bmir.protege.web.shared.perspective.GetPerspectiveLayoutAction;
import edu.stanford.bmir.protege.web.shared.perspective.GetPerspectiveLayoutResult;
import edu.stanford.bmir.protege.web.shared.perspective.PerspectiveId;
import edu.stanford.bmir.protege.web.shared.place.ProjectViewPlace;
import edu.stanford.bmir.protege.web.shared.project.ProjectId;
import edu.stanford.bmir.protege.web.shared.user.UserId;
import edu.stanford.protege.widgetmap.shared.node.Node;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Matthew Horridge, Stanford University, Bio-Medical Informatics Research Group, Date: 16/05/2014
 */
public class PerspectivePresenter implements HasDispose {

    private final ProjectId projectId;

    private final DispatchServiceManager dispatchServiceManager;

    private final PerspectiveView perspectiveView;

    private final Map<PerspectiveId, Perspective> perspectiveCache = new HashMap<>();

    private final Map<PerspectiveId, Node> originalRootNodeMap = new HashMap<>();

    private final PerspectiveFactory perspectiveFactory;

    private Optional<PerspectiveId> currentPerspective = Optional.absent();



    @Inject
    public PerspectivePresenter(PerspectiveView perspectiveView, ProjectId projectId, DispatchServiceManager dispatchServiceManager, PerspectiveFactory perspectiveFactory, EventBus eventBus) {
        this.perspectiveView = perspectiveView;
        this.projectId = projectId;
        this.dispatchServiceManager = dispatchServiceManager;
        this.perspectiveFactory = perspectiveFactory;
        eventBus.addHandler(PlaceChangeEvent.TYPE, new PlaceChangeEvent.Handler() {
            @Override
            public void onPlaceChange(PlaceChangeEvent event) {
                if(event.getNewPlace() instanceof ProjectViewPlace) {
                    displayPerspective(((ProjectViewPlace) event.getNewPlace()).getPerspectiveId());
                }
            }
        });
        eventBus.addHandler(ResetPerspectiveEvent.getType(), new ResetPerspectiveHandler() {
            @Override
            public void handleResetPerspective(ResetPerspectiveEvent event) {
                resetPerspective(event.getPerspectiveId());
            }
        });
    }

    public void start(AcceptsOneWidget container, ProjectViewPlace place) {
        container.setWidget(perspectiveView);
        displayPerspective(place.getPerspectiveId());
    }

    private void resetPerspective(PerspectiveId perspectiveId) {
        GWT.log("[PerspectivePresenter] Reset Perspective: " + perspectiveId);

        Perspective perspective = perspectiveCache.get(perspectiveId);
        if(perspective == null) {
            return;
        }
        Node originalRootNode = originalRootNodeMap.get(perspectiveId);
        if(originalRootNode == null) {
            // Shouldn't happen - if it does then something is broken.
            return;
        }
        perspective.setRootNode(originalRootNode.duplicate());
    }

    private void displayPerspective(final PerspectiveId perspectiveId) {
        GWT.log("[PerspectivePresenter] Display Perspective: " + perspectiveId);
        currentPerspective = Optional.of(perspectiveId);
        retrieveAndSetPerspective(perspectiveId);
    }

    public void removePerspective(PerspectiveId perspectiveId) {
        Perspective perspective = perspectiveCache.remove(perspectiveId);
        if(perspective != null) {
            perspective.dispose();
            if(currentPerspective.equals(Optional.of(perspective))) {
                perspectiveView.setWidget(new Label("Nothing Here"));
            }
        }
    }

    private void retrieveAndSetPerspective(final  PerspectiveId perspectiveId) {
        Perspective p = perspectiveCache.get(perspectiveId);
        if(p != null) {
            perspectiveView.setWidget(p);
            return;
        }
        perspectiveView.setWidget(new Label("Loading..."));
        GWT.log("[PerspectivePresenter] Loading perspective for project " + projectId);
        dispatchServiceManager.execute(new GetPerspectiveLayoutAction(projectId, UserId.getGuest(), perspectiveId),
                new DispatchServiceCallback<GetPerspectiveLayoutResult>() {
                    @Override
                    public void handleSuccess(GetPerspectiveLayoutResult result) {
                        GWT.log("[PerspectivePresenter] Retrieved layout: " + result.getPerspectiveLayout());
                        Perspective perspective = perspectiveFactory.createPerspective(perspectiveId);
                        Node rootNode = result.getPerspectiveLayout().getRootNode();
                        perspective.setRootNode(rootNode);
                        perspectiveCache.put(perspectiveId, perspective);
                        perspectiveView.setWidget(perspective);
                        originalRootNodeMap.put(perspectiveId, rootNode.duplicate());
                    }
                });
    }

    @Override
    public void dispose() {
        for(Perspective tab : perspectiveCache.values()) {
            tab.dispose();
        }
    }

}