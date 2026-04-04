import { notFound } from "next/navigation";

import CategoryFeedPage from "@/components/CategoryFeedPage";
import { getHomeCategoryConfig, HOME_CATEGORY_CHIPS } from "@/lib/home-category-chips";

export const runtime = "nodejs";
export const revalidate = 21600;
export const dynamicParams = false;

export function generateStaticParams() {
  return HOME_CATEGORY_CHIPS.map((category) => ({
    categoryId: category.id
  }));
}

export default async function HomeCategoryPage({
  params
}: {
  params: Promise<{
    categoryId: string;
  }>;
}) {
  const { categoryId } = await params;

  if (!getHomeCategoryConfig(categoryId)) {
    notFound();
  }

  return <CategoryFeedPage categoryId={categoryId} />;
}
