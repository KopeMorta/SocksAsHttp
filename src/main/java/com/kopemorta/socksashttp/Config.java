package com.kopemorta.socksashttp;

import com.kopemorta.socksashttp.entities.BindConfig;
import lombok.Builder;
import lombok.Getter;

import java.util.Collections;
import java.util.List;

@Builder
@Getter
public class Config {
    @Builder.Default
    int bossThreads = 2;

    @Builder.Default
    int workerThreads = 4;

    @Builder.Default
    List<BindConfig> bindConfigList = Collections.singletonList(BindConfig.DEFAULT_CONFIG);
}
