package nu.marginalia.wmsa.auth.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter @AllArgsConstructor
public class LoginFormModel {
    public final String service;
    public final String redirect;
}
