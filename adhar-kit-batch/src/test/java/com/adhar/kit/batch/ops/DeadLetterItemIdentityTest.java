package com.adhar.kit.batch.ops;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What makes two dead letters the same item for the dlq-replay's one write per item (design 0025
 * §5.6, A3.6): the same Java type and the same JSON VALUE. Member order and whitespace are not part
 * of a JSON value, and PostgreSQL's JSONB already drops both, so a text column of an application's
 * own table must not turn one item into two; every digit of a decimal, the order of an array and
 * the type are, so two different items are never merged into one write.
 */
class DeadLetterItemIdentityTest {

    private static final String TYPE = "com.example.Payment";

    @Test
    @DisplayName("the same item whatever the member order and whitespace, nested objects included")
    void memberOrderAndWhitespaceAreNotPartOfTheItem() {
        String stored = "{\"id\":8,\"note\":\"entry-8\",\"payee\":{\"bank\":\"DBS\",\"account\":\"001\"}}";
        String reordered = "{ \"payee\" : { \"account\" : \"001\", \"bank\" : \"DBS\" }, \"note\" : \"entry-8\", \"id\" : 8 }";

        assertThat(DeadLetterReplayer.hash(TYPE, reordered)).isEqualTo(DeadLetterReplayer.hash(TYPE, stored));
    }

    @Test
    @DisplayName("never the same item: another value, a decimal beyond a double's precision, another array order, another type")
    void differentItemsStayDifferent() {
        String item = "{\"id\":8,\"amount\":0.1,\"tags\":[\"a\",\"b\"]}";

        assertThat(DeadLetterReplayer.hash(TYPE, "{\"id\":9,\"amount\":0.1,\"tags\":[\"a\",\"b\"]}"))
                .isNotEqualTo(DeadLetterReplayer.hash(TYPE, item));
        assertThat(DeadLetterReplayer.hash(TYPE, "{\"id\":8,\"amount\":0.10000000000000000001,\"tags\":[\"a\",\"b\"]}"))
                .as("rounded through a double, these two amounts would be one item and one of them never written")
                .isNotEqualTo(DeadLetterReplayer.hash(TYPE, item));
        assertThat(DeadLetterReplayer.hash(TYPE, "{\"id\":8,\"amount\":0.1,\"tags\":[\"b\",\"a\"]}"))
                .isNotEqualTo(DeadLetterReplayer.hash(TYPE, item));
        assertThat(DeadLetterReplayer.hash("com.example.Refund", item)).isNotEqualTo(DeadLetterReplayer.hash(TYPE, item));
    }

    @Test
    @DisplayName("a stored text that is not JSON is compared as it is, never refused")
    void textThatIsNotJsonIsComparedAsText() {
        assertThat(DeadLetterReplayer.canonical("{not json")).isEqualTo("{not json");
        assertThat(DeadLetterReplayer.hash(TYPE, "{not json")).isEqualTo(DeadLetterReplayer.hash(TYPE, "{not json"))
                .isNotEqualTo(DeadLetterReplayer.hash(TYPE, "{not  json"));
    }
}
