import type { Meta, StoryObj } from "@storybook/react";
import { createKcPageStory } from "../KcPageStory";

const { KcPageStory } = createKcPageStory({ pageId: "email-code-verify.ftl" });

const meta = {
    title: "login/email-code-verify.ftl",
    component: KcPageStory
} satisfies Meta<typeof KcPageStory>;

export default meta;

type Story = StoryObj<typeof meta>;

export const SenhaPrimeiro: Story = {
    render: () => (
        <KcPageStory
            kcContext={{
                mjMode: "password",
                mjCooldown: 0
            }}
        />
    )
};

export const CodigoEnviado: Story = {
    render: () => <KcPageStory />
};

export const CodigoEntrouPorCpf: Story = {
    render: () => (
        <KcPageStory
            kcContext={{
                mjMode: "code",
                mjDest: "",
                mjTyped: "339.982.918-30",
                mjCooldown: 0
            }}
        />
    )
};

export const SenhaInvalida: Story = {
    render: () => (
        <KcPageStory
            kcContext={{
                mjMode: "password",
                mjCooldown: 0,
                message: { type: "error", summary: "Dados de acesso inválidos." }
            }}
        />
    )
};
