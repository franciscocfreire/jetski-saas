import type { Meta, StoryObj } from "@storybook/react";
import { createKcPageStory } from "../KcPageStory";

const { KcPageStory } = createKcPageStory({ pageId: "email-code-id.ftl" });

const meta = {
    title: "login/email-code-id.ftl",
    component: KcPageStory
} satisfies Meta<typeof KcPageStory>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Default: Story = {
    render: () => <KcPageStory />
};

export const SemSocial: Story = {
    render: () => (
        <KcPageStory
            kcContext={{
                mjSocial: []
            }}
        />
    )
};

export const CodigoExpirado: Story = {
    render: () => (
        <KcPageStory
            kcContext={{
                message: { type: "error", summary: "Código expirado — peça um novo." }
            }}
        />
    )
};
