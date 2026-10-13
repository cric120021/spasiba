import { notFound } from "next/navigation";

import CategoryFeedPage from "@/components/CategoryFeedPage";
import { getHomeCategoryConfig, HOME_CATEGORY_CHIPS } from "@/lib/home-category-chips";
import { getHomeCategoryFeed } from "@/lib/home-category-feed";

export const runtime = "nodejs";
export const revalidate = 604800;
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

  const initialFeed = await getHomeCategoryFeed(categoryId);

  if (!initialFeed) {
    notFound();
  }

  return <CategoryFeedPage categoryId={categoryId} initialFeed={initialFeed} />;
}
