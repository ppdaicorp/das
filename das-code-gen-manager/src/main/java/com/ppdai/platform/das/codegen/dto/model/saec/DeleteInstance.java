package com.ppdai.platform.das.codegen.dto.model.saec;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeleteInstance {
    private InfoSaec secInfoDto;
    private DataInst data;
}
