package com.restaurant.workforce.member.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MemberRepositoryPositionTests {

    @Test
    void legacyJobTitlesHaveTheSameAssignmentCodesAsNewMemberships() {
        assertThat(MemberRepository.positionCode("Официант")).isEqualTo("WAITER");
        assertThat(MemberRepository.positionCode(" waiter ")).isEqualTo("WAITER");
        assertThat(MemberRepository.positionCode("Раннер")).isEqualTo("RUNNER");
        assertThat(MemberRepository.positionCode("Помощник по залу")).isEqualTo("RUNNER");
        assertThat(MemberRepository.positionCode("RUNNER")).isEqualTo("RUNNER");
    }

    @Test
    void customPositionsAreNotGrantedWaiterPermissions() {
        assertThat(MemberRepository.positionCode("Бармен")).isEqualTo("БАРМЕН");
        assertThat(MemberRepository.positionCode(null)).isNull();
    }
}
