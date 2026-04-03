package com.borjaglez.specrepository.core;

public sealed interface QueryCondition permits GroupCondition, PredicateCondition {}
