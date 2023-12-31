package io.github.ctliv.eventbus.event;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import io.github.ctliv.eventbus.util.EbaUtl;
import io.github.ctliv.eventbus.EventBusAwareScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.EventObject;
import java.util.Optional;
import java.util.function.Predicate;

public class BaseEvent extends EventObject {

    private static final boolean SPRING_SECURITY_DETECTED;

    private static final Logger log = LoggerFactory.getLogger(BaseEvent.class);

    static {
        String basePackage = "org.springframework.security";
        SPRING_SECURITY_DETECTED =
                EbaUtl.exists(basePackage + ".authentication.AnonymousAuthenticationToken") &&
                EbaUtl.exists(basePackage + ".core.Authentication") &&
                EbaUtl.exists(basePackage + ".core.context.SecurityContextHolder");
        log.debug("EventBusAware: Spring security "  + (SPRING_SECURITY_DETECTED ? "": "not ") + "detected");
    }

    public static <T extends BaseEvent> Predicate<BaseEvent> isInstanceOf(BaseEvent event, Class<T> type) {
        EbaUtl.allNotNull(event, type);
        return baseEvent -> type.isInstance(event);
    }

    public static Predicate<BaseEvent> generatedInCurrentThread() {
        return event -> Thread.currentThread().equals(event.getThread());
    }

    public static Predicate<BaseEvent> fromCurrentUI() {
        return fromUI(UI.getCurrent());
    }

    public static Predicate<BaseEvent> fromUI(UI... uis) {
        return event -> EbaUtl.in(event.getUi(), uis);
    }

    public static Predicate<BaseEvent> fromSource(Object... objects) {
        return event -> EbaUtl.in(event.getSource(), objects);
    }

    public static Predicate<BaseEvent> fromMe() {
        return event -> {
            if (!SPRING_SECURITY_DETECTED) throw new IllegalStateException("Spring security not detected");
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || authentication instanceof  AnonymousAuthenticationToken) {
                return event.isAnonymousUser();
            } else {
                Object sender = event.getUser().orElse(null);
                try {
                    return authentication.getPrincipal().equals(sender);
                } catch (Exception ignored) { /* Noop*/ }
                return false;
            }
        };
    }

    public static Predicate<BaseEvent> withScope(EventBusAwareScope... scopes) {
        return event -> EbaUtl.in(event.getScope(), scopes);
    }

    public static Predicate<BaseEvent> withUiScope() {
        return withScope(EventBusAwareScope.VUI);
    }

    private EventBusAwareScope scope = null;
    private boolean anonymousUser;
    private transient Object user;
    private String username;
    private final UI ui;
    private final transient Thread thread;
    private final transient ArrayList<BaseEvent> followers = new ArrayList<>();

    public BaseEvent(Object source) {
        super(source);

        if (SPRING_SECURITY_DETECTED) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
                anonymousUser = true;
            } else {
                anonymousUser = false;
                try {
                    this.user = authentication.getPrincipal();
                } catch (Exception ignored) { /* Noop*/ }
                try {
                    this.username = authentication.getName();
                } catch (Exception ignored) { /* Noop*/ }
            }
        }
        this.ui = UI.getCurrent();
        thread = Thread.currentThread();
    }

    public EventBusAwareScope getScope() {
        return scope;
    }

    public void setScope(EventBusAwareScope scope) {
        this.scope = scope;
    }

    public boolean isAnonymousUser() {
        return anonymousUser;
    }

    public Optional<Object> getUser() {
        return Optional.ofNullable(user);
    }

    public Optional<String> getUsername() {
        return Optional.ofNullable(username);
    }

    public UI getUi() {
        return ui;
    }

    public Thread getThread() {
        return thread;
    }

    public ArrayList<BaseEvent> getFollowers() { return followers; }

    public Optional<BaseEvent> optional(Predicate<BaseEvent> predicate) {
        return check(predicate) ? Optional.of(this) : Optional.empty();
    }

    public boolean check(Predicate<BaseEvent> predicate) {
        EbaUtl.notNull(predicate);
        return predicate.test(this);
    }

    public boolean check(Predicate<BaseEvent> predicate, Runnable runnable) {
        return check(predicate, UI.getCurrent(), runnable);
    }

    public boolean check(Predicate<BaseEvent> predicate, UI ui, Runnable runnable) {
        boolean result = check(predicate);
        if (result) exec(ui, runnable);
        return result;
    }

    public boolean check(Predicate<BaseEvent> predicate, Component component, Runnable runnable) {
        EbaUtl.allNotNull(predicate, component, runnable);
        boolean result = check(predicate);
        if (result) exec(component.getUI().orElse(null), runnable);
        return result;
    }

    public void exec(Runnable runnable) {
        EbaUtl.notNull(runnable);
        exec(UI.getCurrent(), runnable);
    }

    public void exec(UI ui, Runnable runnable) {
        EbaUtl.notNull(runnable);
//        String caller = LogUtils.getCallerOf(this.getClass().getPackageName());
//        log.trace(LogUtils.getDescr(this) + " executing task for " + caller);
        if (ui == null)
            runnable.run();
        else if (!Thread.currentThread().equals(this.getThread()))
            ui.access(runnable::run);
        else if (ui.equals(UI.getCurrent()))
            runnable.run();
        else
            ui.access(runnable::run);
    }

}
