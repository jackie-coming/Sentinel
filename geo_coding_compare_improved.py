# -*- coding: utf-8
import json
import logging
from typing import Dict, List, Tuple, Optional
from dataclasses import dataclass
from enum import Enum

# 配置日志
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

class FieldType(Enum):
    """字段类型枚举"""
    EXACT_MATCH = "exact_match"          # 精确匹配
    TEXT_SIMILARITY = "text_similarity"  # 文本相似度匹配
    SPECIAL_RULE = "special_rule"        # 特殊规则匹配

@dataclass
class CompareResult:
    """比对结果数据类"""
    total_samples: int
    valid_samples: int
    failed_requests: int
    field_differences: Dict[str, int]
    
    def get_difference_rate(self, field: str) -> float:
        """获取字段差异率"""
        if self.valid_samples == 0:
            return 0.0
        return (self.field_differences.get(field, 0) / self.valid_samples) * 100

class GeoCodingComparator:
    """地理编码比对器"""
    
    def __init__(self, similarity_threshold: float = 0.6):
        self.similarity_threshold = similarity_threshold
        
        # 字段配置：字段名 -> 比对类型
        self.field_config = {
            "formattedAddress": FieldType.TEXT_SIMILARITY,
            "adCode": FieldType.EXACT_MATCH,
            "city": FieldType.EXACT_MATCH,
            "cityCode": FieldType.EXACT_MATCH,
            "country": FieldType.EXACT_MATCH,
            "district": FieldType.TEXT_SIMILARITY,
            "province": FieldType.EXACT_MATCH,
            "townCode": FieldType.SPECIAL_RULE,
            "townShip": FieldType.TEXT_SIMILARITY
        }
        
        # 可以为空的字段
        self.nullable_fields = {"city", "district", "cityCode"}
    
    def get_text_similarity_score(self, text1: str, text2: str) -> float:
        """计算文本相似度"""
        if not text1 and not text2:
            return 1.0
        if not text1 or not text2:
            return 0.0
        
        # 这里应该调用实际的文本相似度计算方法
        # 暂时使用简单的字符串比较作为示例
        if text1 == text2:
            return 1.0
        
        # 简单的Jaccard相似度计算（实际应该使用余弦相似度）
        set1 = set(text1)
        set2 = set(text2)
        intersection = len(set1.intersection(set2))
        union = len(set1.union(set2))
        
        return intersection / union if union > 0 else 0.0
    
    def compare_field(self, field_name: str, gaode_value: str, hello_value: str) -> bool:
        """比对单个字段
        
        Returns:
            bool: True表示相同，False表示不同
        """
        field_type = self.field_config.get(field_name, FieldType.EXACT_MATCH)
        
        # 处理空值情况
        if field_name in self.nullable_fields:
            if not gaode_value:  # 高德为空则跳过
                return True
        
        if field_type == FieldType.EXACT_MATCH:
            return gaode_value == hello_value
            
        elif field_type == FieldType.TEXT_SIMILARITY:
            similarity = self.get_text_similarity_score(gaode_value, hello_value)
            return similarity >= self.similarity_threshold
            
        elif field_type == FieldType.SPECIAL_RULE:
            return self._apply_special_rule(field_name, gaode_value, hello_value)
        
        return False
    
    def _apply_special_rule(self, field_name: str, gaode_value: str, hello_value: str) -> bool:
        """应用特殊规则"""
        if field_name == "townCode":
            # townCode特殊规则：Hello是9位，高德是12位（后面补000）
            if len(hello_value) == 9 and len(gaode_value) == 12:
                return gaode_value == hello_value + "000"
        
        # 默认精确匹配
        return gaode_value == hello_value
    
    def extract_geo_data(self, data_item: dict) -> Optional[dict]:
        """提取地理编码数据"""
        try:
            result = data_item['modelOutParam']['result']
            if 'data' not in result:
                return None
                
            geo_codes_list = result['data']['reGeoCodesList']
            if not geo_codes_list:
                return None
                
            return geo_codes_list[0]
        except (KeyError, IndexError) as e:
            logger.warning(f"数据提取失败: {e}")
            return None
    
    def compare_datasets(self, gaode_file: str, hello_file: str) -> CompareResult:
        """比对两个数据集"""
        logger.info("开始加载数据文件...")
        
        # 加载数据
        with open(gaode_file, "r", encoding='utf-8') as f:
            gaode_data = json.load(f)['data']
        
        with open(hello_file, "r", encoding='utf-8') as f:
            hello_data = json.load(f)['data']
        
        total_samples = len(gaode_data)
        failed_requests = 0
        field_differences = {}
        
        logger.info(f"开始比对 {total_samples} 条数据...")
        
        for index in range(total_samples):
            # 提取地理编码数据
            gaode_geo = self.extract_geo_data(gaode_data[index])
            hello_geo = self.extract_geo_data(hello_data[index])
            
            if gaode_geo is None or hello_geo is None:
                failed_requests += 1
                logger.debug(f"第 {index} 条数据提取失败")
                continue
            
            # 比对formattedAddress
            self._compare_formatted_address(gaode_geo, hello_geo, field_differences)
            
            # 比对addressComponent各字段
            self._compare_address_components(gaode_geo, hello_geo, field_differences)
        
        valid_samples = total_samples - failed_requests
        
        return CompareResult(
            total_samples=total_samples,
            valid_samples=valid_samples,
            failed_requests=failed_requests,
            field_differences=field_differences
        )
    
    def _compare_formatted_address(self, gaode_geo: dict, hello_geo: dict, 
                                 field_differences: dict) -> None:
        """比对格式化地址"""
        field_name = "formattedAddress"
        
        gaode_addr = gaode_geo.get(field_name, "")
        hello_addr = hello_geo.get(field_name, "")
        
        if not self.compare_field(field_name, gaode_addr, hello_addr):
            field_differences[field_name] = field_differences.get(field_name, 0) + 1
            logger.debug(f"formattedAddress差异: {gaode_addr} vs {hello_addr}")
    
    def _compare_address_components(self, gaode_geo: dict, hello_geo: dict, 
                                  field_differences: dict) -> None:
        """比对地址组件"""
        gaode_component = gaode_geo.get("addressComponent", {})
        hello_component = hello_geo.get("addressComponent", {})
        
        check_fields = ["adCode", "city", "cityCode", "country", "district", 
                       "province", "townCode", "townShip"]
        
        for field in check_fields:
            gaode_value = gaode_component.get(field, "")
            hello_value = hello_component.get(field, "")
            
            # 检查Hello是否缺少该字段
            if field in gaode_component and field not in hello_component:
                if field != "cityCode":  # cityCode缺失不算差异
                    field_differences[field] = field_differences.get(field, 0) + 1
                    logger.debug(f"{field} Hello缺失: {gaode_value}")
                continue
            
            # 比对字段值
            if not self.compare_field(field, gaode_value, hello_value):
                field_differences[field] = field_differences.get(field, 0) + 1
                logger.debug(f"{field}差异: {gaode_value} vs {hello_value}")
    
    def print_comparison_report(self, result: CompareResult) -> None:
        """打印比对报告"""
        print("\n" + "="*50)
        print("地理编码比对报告")
        print("="*50)
        print(f"总样本数: {result.total_samples}")
        print(f"有效样本数: {result.valid_samples}")
        print(f"失败请求数: {result.failed_requests}")
        print(f"成功率: {((result.valid_samples / result.total_samples) * 100):.2f}%")
        
        print("\n字段差异统计:")
        print("-" * 30)
        
        for field, diff_count in result.field_differences.items():
            diff_rate = result.get_difference_rate(field)
            print(f"{field:15} | 差异数: {diff_count:4d} | 差异率: {diff_rate:6.2f}%")
        
        print("="*50)

def main():
    """主函数"""
    # 配置文件路径
    gaode_file = "gaode_geocoding_log.json"
    hello_file = "hello_geocoding_log.json"
    
    # 创建比对器
    comparator = GeoCodingComparator(similarity_threshold=0.6)
    
    try:
        # 执行比对
        result = comparator.compare_datasets(gaode_file, hello_file)
        
        # 打印报告
        comparator.print_comparison_report(result)
        
    except FileNotFoundError as e:
        logger.error(f"文件未找到: {e}")
    except Exception as e:
        logger.error(f"比对过程出错: {e}")

if __name__ == "__main__":
    main() 