package com.borjaglez.specrepository.core;

import static org.apiguardian.api.API.Status.STABLE;

import org.apiguardian.api.API;

@API(status = STABLE, since = "1.0.0")
public enum SubqueryKind {
  EXISTS,
  NOT_EXISTS,
  IN,
  NOT_IN
}
